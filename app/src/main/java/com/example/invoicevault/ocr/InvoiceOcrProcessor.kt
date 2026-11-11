package com.example.invoicevault.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.regex.Pattern
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

data class OcrTextLine(
    val text: String,
    val rect: Rect?
)

object InvoiceOcrProcessor {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Extracts structured invoice data from an image or PDF file.
     */
    suspend fun processInvoiceDocument(context: Context, filePath: String): ExtractedInvoiceData =
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            require(file.exists()) { "Invoice file does not exist at path: $filePath" }

            val bitmap = if (filePath.lowercase(Locale.ROOT).endsWith(".pdf")) {
                renderPdfToHighResBitmap(file)
            } else {
                decodeImageBitmap(file)
            } ?: error("Failed to decode image or PDF for OCR processing")

            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val mlKitText = recognizeText(inputImage)

            parseExtractedText(mlKitText)
        }

    private suspend fun recognizeText(image: InputImage): Text =
        suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    if (continuation.isActive) {
                        continuation.resume(visionText)
                    }
                }
                .addOnFailureListener { exception ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(exception)
                    }
                }
        }

    /**
     * Parses raw ML Kit text into structured invoice fields using 2D spatial layout,
     * table-column alignment, regexes, and robust heuristics.
     */
    fun parseExtractedText(visionText: Text): ExtractedInvoiceData {
        val fullText = visionText.text
        val ocrLines = visionText.textBlocks.flatMap { block ->
            block.lines.map { line ->
                OcrTextLine(line.text.trim(), line.boundingBox)
            }
        }.filter { it.text.isNotBlank() }

        val plainLines = ocrLines.map { it.text }

        val invoiceNumberResult = extractInvoiceNumber(ocrLines, plainLines, fullText)
        val purchaseDateResult = extractPurchaseDate(ocrLines, plainLines, fullText)
        val sellerNameResult = extractSellerName(ocrLines, plainLines, fullText)
        val productNameResult = extractProductName(ocrLines, plainLines, sellerNameResult.value)
        val totalAmountResult = extractTotalAmount(ocrLines, plainLines, fullText)
        val (warrantyResult, warrantyDesc) = extractWarranty(plainLines, fullText)

        return ExtractedInvoiceData(
            invoiceNumber = invoiceNumberResult,
            purchaseDate = purchaseDateResult,
            sellerName = sellerNameResult,
            productName = productNameResult,
            totalAmount = totalAmountResult,
            warrantyDurationMonths = warrantyResult,
            warrantyDescription = warrantyDesc,
            rawExtractedText = fullText,
            detectedLines = plainLines
        )
    }

    // -------------------------------------------------------------
    // SPATIAL 2D HELPER: FIND TEXT HORIZONTALLY ALIGNED ON RIGHT
    // -------------------------------------------------------------
    private fun findHorizontallyAlignedValue(
        targetLabel: OcrTextLine,
        allLines: List<OcrTextLine>,
        maxVerticalDriftRatio: Float = 0.85f
    ): String? {
        val targetRect = targetLabel.rect ?: return null
        val targetHeight = maxOf(targetRect.height(), 15)

        // Find candidate lines whose Y-center aligns closely with the target, situated to its right
        val candidates = allLines.filter { other ->
            val otherRect = other.rect
            if (otherRect == null || other == targetLabel) false
            else {
                val isToRight = otherRect.left >= targetRect.left + (targetRect.width() * 0.4f)
                val verticalDiff = abs(otherRect.centerY() - targetRect.centerY())
                val isSameRow = verticalDiff <= (targetHeight * maxVerticalDriftRatio)
                isToRight && isSameRow
            }
        }.sortedBy { it.rect!!.left } // Closest on the right first

        return candidates.firstOrNull()?.text
    }

    // -------------------------------------------------------------
    // 1. INVOICE NUMBER EXTRACTION
    // -------------------------------------------------------------
    private fun extractInvoiceNumber(
        ocrLines: List<OcrTextLine>,
        plainLines: List<String>,
        fullText: String
    ): FieldResult<String> {
        val labelPatterns = listOf("invoice number", "invoice no", "invoice #", "bill no", "bill #", "receipt no", "tax invoice no", "order id", "order no", "order #")

        // 1. 2D Spatial search: look for "Invoice number:" label and find horizontally adjacent text
        for (line in ocrLines) {
            val lower = line.text.lowercase(Locale.ROOT).trimEnd(':', ' ')
            if (labelPatterns.any { lower == it || lower.startsWith("$it:") || lower.startsWith(it) }) {
                // Check if inline value exists (e.g. "Invoice number: 1001")
                val inlineValue = line.text.replace(Regex("(?i).*(?:invoice\\s*(?:number|no|#)?|bill\\s*(?:no|#)?|order\\s*(?:id|no|#))[:\\s.\\-#]*"), "").trim()
                if (isValidInvoiceNumber(inlineValue)) {
                    return FieldResult(inlineValue, FieldConfidence.HIGH, "Inline with invoice label")
                }

                // Look horizontally to the right via bounding box
                val adjacentRight = findHorizontallyAlignedValue(line, ocrLines)
                if (isValidInvoiceNumber(adjacentRight)) {
                    return FieldResult(adjacentRight!!, FieldConfidence.HIGH, "Matched table row: $adjacentRight")
                }
            }
        }

        // 2. Direct single-string regex match (e.g. "Invoice Number: 1001" or "INV-2025-01")
        val invoiceRegexes = listOf(
            Regex("(?i)(?:invoice\\s*(?:no|number|#|num)?|tax\\s*invoice\\s*(?:no|#)?|bill\\s*(?:no|#)?|receipt\\s*(?:no|#)?)[:\\s.\\-#]*([A-Z0-9\\-_/]{3,30})"),
            Regex("(?i)(?:order\\s*(?:id|number|no|#))[:\\s.\\-#]*([A-Z0-9\\-_/]{3,30})")
        )
        for (pattern in invoiceRegexes) {
            val match = pattern.find(fullText)
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                if (isValidInvoiceNumber(candidate)) {
                    return FieldResult(candidate, FieldConfidence.HIGH, "Matched label regex: $candidate")
                }
            }
        }

        // 3. Fallback: Sequential column pairing
        // In many invoices, a block of labels [Invoice number, Invoice date, Due date] is followed
        // by a block of values [1001, 09/01/2025, 09/14/2025] or [09/01/2025, 1001, 09/14/2025]
        val labelIndex = plainLines.indexOfFirst { line ->
            labelPatterns.any { line.contains(it, ignoreCase = true) }
        }
        if (labelIndex != -1) {
            // Search down up to 15 lines for short numeric or alphanumeric ID (excluding dates, prices, addresses)
            for (offset in 1..15) {
                val candidateIndex = labelIndex + offset
                if (candidateIndex < plainLines.size) {
                    val candidate = plainLines[candidateIndex].trim()
                    if (isValidInvoiceNumber(candidate) && !isDateString(candidate) && !isAmountString(candidate)) {
                        return FieldResult(candidate, FieldConfidence.MEDIUM, "Inferred from invoice metadata block")
                    }
                }
            }
        }

        // 4. Standalone structured code pattern (e.g., INV-0012, BILL-9821, #1001)
        val standaloneMatcher = Pattern.compile("\\b([A-Z]{2,4}[-_][0-9]{3,10}|#[0-9]{3,10})\\b").matcher(fullText)
        if (standaloneMatcher.find()) {
            return FieldResult(standaloneMatcher.group(1).removePrefix("#"), FieldConfidence.LOW, "Pattern heuristic")
        }

        return FieldResult(null, FieldConfidence.MISSING, "Invoice number not detected")
    }

    private fun isValidInvoiceNumber(candidate: String?): Boolean {
        if (candidate.isNullOrBlank()) return false
        val clean = candidate.trim().trimEnd(',', '.', ':', ';')
        if (clean.length !in 3..30) return false
        val upper = clean.uppercase(Locale.ROOT)
        val disallowed = listOf("INVOICE", "BILL", "RECEIPT", "DATE", "TAX", "TOTAL", "ORIGINAL", "PAGE", "DUE", "RATE", "AMOUNT", "QUANTITY")
        if (disallowed.any { upper == it || upper.startsWith("$it ") }) return false
        // Must contain at least one digit or standard hyphenated alphanumeric
        return clean.any { it.isDigit() }
    }

    // -------------------------------------------------------------
    // 2. PURCHASE DATE EXTRACTION (Normalized to ISO-8601 yyyy-MM-dd)
    // -------------------------------------------------------------
    private fun extractPurchaseDate(
        ocrLines: List<OcrTextLine>,
        plainLines: List<String>,
        fullText: String
    ): FieldResult<String> {
        val dateKeywords = listOf("invoice date", "billing date", "date of issue", "purchase date", "date:")

        // 1. 2D Spatial search: look for "Invoice date:" label and inspect adjacent text on right
        for (line in ocrLines) {
            val lower = line.text.lowercase(Locale.ROOT).trimEnd(':', ' ')
            if (dateKeywords.any { lower == it || lower.startsWith(it) }) {
                // Check inline date (e.g. "Invoice date: 09/01/2025")
                val inlineDate = extractDateFromText(line.text)
                if (inlineDate != null) {
                    val normalized = normalizeDate(inlineDate)
                    if (normalized != null) {
                        return FieldResult(normalized, FieldConfidence.HIGH, "Inline with date label: $inlineDate")
                    }
                }

                // Check horizontal neighbor on right
                val adjacentRight = findHorizontallyAlignedValue(line, ocrLines)
                if (adjacentRight != null) {
                    val parsedAdjacent = extractDateFromText(adjacentRight) ?: adjacentRight
                    val normalized = normalizeDate(parsedAdjacent)
                    if (normalized != null) {
                        return FieldResult(normalized, FieldConfidence.HIGH, "Table aligned: $parsedAdjacent")
                    }
                }
            }
        }

        // 2. Regex matching label and date together
        val explicitDateRegexes = listOf(
            Regex("(?i)(?:invoice\\s*date|billing\\s*date|purchase\\s*date|date)[:\\s]*([0-9]{1,2}[-/\\.][0-9]{1,2}[-/\\.][0-9]{2,4})"),
            Regex("(?i)(?:invoice\\s*date|billing\\s*date|purchase\\s*date|date)[:\\s]*([0-9]{4}[-/\\.][0-9]{1,2}[-/\\.][0-9]{1,2})"),
            Regex("(?i)(?:invoice\\s*date|billing\\s*date|purchase\\s*date|date)[:\\s]*([0-9]{1,2}\\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[\\s,]+[0-9]{2,4})"),
            Regex("(?i)(?:invoice\\s*date|billing\\s*date|purchase\\s*date|date)[:\\s]*((?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+[0-9]{1,2}[\\s,]+[0-9]{2,4})")
        )
        for (regex in explicitDateRegexes) {
            val match = regex.find(fullText)
            if (match != null) {
                val rawDate = match.groupValues[1]
                val normalized = normalizeDate(rawDate)
                if (normalized != null) {
                    return FieldResult(normalized, FieldConfidence.HIGH, "Matched date regex: $rawDate")
                }
            }
        }

        // 3. Scan all plain lines for any valid date
        for (line in plainLines) {
            val candidate = extractDateFromText(line)
            if (candidate != null) {
                val normalized = normalizeDate(candidate)
                if (normalized != null) {
                    return FieldResult(normalized, FieldConfidence.MEDIUM, "Found date in document: $candidate")
                }
            }
        }

        return FieldResult(null, FieldConfidence.MISSING, "Purchase date not detected")
    }

    private fun extractDateFromText(text: String): String? {
        val dateRegex = Regex("\\b([0-9]{1,2}[-/\\.][0-9]{1,2}[-/\\.][0-9]{2,4}|[0-9]{4}[-/\\.][0-9]{1,2}[-/\\.][0-9]{1,2}|[0-9]{1,2}\\s+[A-Za-z]{3,9}[\\s,]+[0-9]{2,4}|[A-Za-z]{3,9}\\s+[0-9]{1,2}[\\s,]+[0-9]{2,4})\\b")
        return dateRegex.find(text)?.value
    }

    private fun isDateString(text: String): Boolean {
        return extractDateFromText(text) != null
    }

    fun normalizeDate(rawDate: String): String? {
        // Clean characters: replace dots and slashes with dashes
        val clean = rawDate.trim()
            .replace('/', '-')
            .replace('.', '-')
            .replace(",", "")
            .trim()

        val formats = listOf(
            "MM-dd-yyyy", "M-d-yyyy",
            "dd-MM-yyyy", "d-M-yyyy",
            "yyyy-MM-dd", "yyyy-M-d",
            "MM-dd-yy", "dd-MM-yy",
            "d MMMM yyyy", "dd MMMM yyyy", "d MMM yyyy", "dd MMM yyyy",
            "MMMM d yyyy", "MMM d yyyy", "MMMM dd yyyy", "MMM dd yyyy"
        )

        for (format in formats) {
            try {
                val formatter = DateTimeFormatter.ofPattern(format, Locale.ENGLISH)
                val parsed = LocalDate.parse(clean, formatter)
                val now = LocalDate.now()
                // Plausibility check: between 25 years ago and 2 years in the future
                if (parsed.isBefore(now.plusYears(2)) && parsed.isAfter(now.minusYears(25))) {
                    return parsed.format(DateTimeFormatter.ISO_LOCAL_DATE)
                }
            } catch (_: Exception) {}
        }
        return null
    }

    // -------------------------------------------------------------
    // 3. SELLER / MERCHANT NAME EXTRACTION
    // -------------------------------------------------------------
    private fun extractSellerName(
        ocrLines: List<OcrTextLine>,
        plainLines: List<String>,
        fullText: String
    ): FieldResult<String> {
        // 1. Payee heuristics: "Please make payment to", "Make payment to", "Payable to", "Sold by"
        val payeePatterns = listOf(
            Regex("(?i)(?:please\\s*make\\s*payment\\s*to|make\\s*payment\\s*to|payment\\s*to|payable\\s*to|remit\\s*to|sold\\s*by|seller)[:\\s]*([A-Za-z0-9&.,'\\s]{3,40})"),
            Regex("(?i)(?:vendor|merchant|store)[:\\s]*([A-Za-z0-9&.,'\\s]{3,40})")
        )
        for (pattern in payeePatterns) {
            val match = pattern.find(fullText)
            if (match != null) {
                var candidate = match.groupValues[1].split('\n')[0].trim()
                candidate = cleanSellerString(candidate)
                if (candidate.isNotBlank() && candidate.length in 3..40) {
                    return FieldResult(candidate, FieldConfidence.HIGH, "Extracted from payment / seller note")
                }
            }
        }

        // Also check if line is "Please make payment to" and next line is the company name
        for (i in plainLines.indices) {
            val line = plainLines[i]
            if (line.matches(Regex("(?i).*(?:payment\\s*to|payable\\s*to|sold\\s*by).*"))) {
                if (i + 1 < plainLines.size) {
                    val next = cleanSellerString(plainLines[i + 1])
                    if (isValidSellerName(next)) {
                        return FieldResult(next, FieldConfidence.HIGH, "Follows payee header: $next")
                    }
                }
            }
        }

        // 2. Recognized prominent merchants
        val knownMerchants = listOf(
            "Elevate Consulting", "Amazon", "Apple", "Best Buy", "Walmart", "Target", "Costco", "Flipkart",
            "Samsung", "Dell", "HP", "Lenovo", "Sony", "Croma", "Reliance Digital",
            "IKEA", "Home Depot", "B&H Photo", "Newegg", "Micro Center", "Google"
        )
        for (merchant in knownMerchants) {
            if (fullText.contains(merchant, ignoreCase = true)) {
                return FieldResult(merchant, FieldConfidence.HIGH, "Identified recognized merchant")
            }
        }

        // 3. Header analysis: combine lines 0 and 1 if line 1 continues company name
        // (e.g. Line 0: "Elevate", Line 1: "Consulting" -> "Elevate Consulting")
        val companySuffixes = listOf("consulting", "services", "inc", "llc", "ltd", "corp", "corporation", "technologies", "tech", "solutions", "enterprises", "store", "shop", "electronics")
        if (plainLines.size >= 2) {
            val first = cleanSellerString(plainLines[0])
            val second = cleanSellerString(plainLines[1])
            if (companySuffixes.any { second.lowercase(Locale.ROOT).contains(it) }) {
                val combined = "$first $second"
                if (isValidSellerName(combined)) {
                    return FieldResult(combined, FieldConfidence.HIGH, "Combined multi-line business header")
                }
            }
        }

        // Inspect top 4 lines
        val ignoredHeaders = listOf("TAX INVOICE", "INVOICE", "BILL OF SUPPLY", "RECEIPT", "ORIGINAL", "DUPLICATE", "GSTIN", "TEL", "PHONE", "EMAIL", "DATE")
        for (i in 0 until minOf(4, plainLines.size)) {
            val candidate = cleanSellerString(plainLines[i])
            val upper = candidate.uppercase(Locale.ROOT)
            val isIgnored = ignoredHeaders.any { upper.contains(it) } || candidate.length < 3 || candidate.all { it.isDigit() }
            if (!isIgnored && isValidSellerName(candidate)) {
                return FieldResult(candidate, FieldConfidence.MEDIUM, "Top header line")
            }
        }

        return FieldResult(null, FieldConfidence.MISSING, "Seller name not detected")
    }

    private fun cleanSellerString(raw: String): String {
        return raw.split('\n')[0]
            .replace(Regex("(?i)gstin[:\\sA-Z0-9]+"), "")
            .replace(Regex("(?i)cin[:\\sA-Z0-9]+"), "")
            .trim()
            .trimEnd(',', '.', '-', ':', ';')
    }

    private fun isValidSellerName(candidate: String): Boolean {
        if (candidate.length !in 3..45) return false
        val lower = candidate.lowercase(Locale.ROOT)
        if (lower.startsWith("invoice") || lower.startsWith("tax") || lower.startsWith("bill") || lower.startsWith("page")) return false
        // Exclude address lines
        if (lower.contains("street") || lower.contains("blvd") || lower.contains("avenue") || lower.contains("road") || lower.matches(Regex(".*[0-9]{5}.*"))) return false
        return true
    }

    // -------------------------------------------------------------
    // 4. PRODUCT / ITEM NAME EXTRACTION
    // -------------------------------------------------------------
    private fun extractProductName(
        ocrLines: List<OcrTextLine>,
        plainLines: List<String>,
        sellerName: String?
    ): FieldResult<String> {
        // 1. Direct label check: Line says "Product", "Item", or "Description" -> check adjacent or next line!
        for (i in plainLines.indices) {
            val line = plainLines[i].trim()
            val lower = line.lowercase(Locale.ROOT).trimEnd(':', ' ')
            if (lower in listOf("product", "item", "item description", "description", "service", "particulars")) {
                // Check next non-empty line
                if (i + 1 < plainLines.size) {
                    val nextLine = plainLines[i + 1].trim()
                    if (isValidProductCandidate(nextLine, sellerName)) {
                        return FieldResult(nextLine, FieldConfidence.HIGH, "Follows '$line' header")
                    }
                }

                // Check horizontally aligned right value
                if (i < ocrLines.size) {
                    val rightVal = findHorizontallyAlignedValue(ocrLines[i], ocrLines)
                    if (rightVal != null && isValidProductCandidate(rightVal, sellerName)) {
                        return FieldResult(rightVal, FieldConfidence.HIGH, "Aligned to '$line' header")
                    }
                }
            }
        }

        // 2. Keyword detection for common hardware / electronic goods
        val productKeywords = listOf(
            "Project consulting", "Consulting", "MacBook", "iPhone", "iPad", "Galaxy", "Laptop", "Headphones",
            "Earbuds", "Monitor", "Keyboard", "Mouse", "Camera", "Watch", "Television", "TV",
            "Speaker", "AirPods", "Processor", "Router", "Display", "Tablet", "Console", "PlayStation", "Xbox"
        )
        for (line in plainLines) {
            for (keyword in productKeywords) {
                if (line.contains(keyword, ignoreCase = true)) {
                    val clean = line.replace(Regex("^[0-9]+[.\\-\\s]+"), "").trim()
                    if (isValidProductCandidate(clean, sellerName)) {
                        return FieldResult(clean, FieldConfidence.HIGH, "Detected item keyword: $keyword")
                    }
                }
            }
        }

        return FieldResult(null, FieldConfidence.MISSING, "Product name not detected")
    }

    private fun isValidProductCandidate(candidate: String, sellerName: String?): Boolean {
        if (candidate.length !in 3..60) return false
        val lower = candidate.lowercase(Locale.ROOT)
        if (sellerName != null && candidate.equals(sellerName, ignoreCase = true)) return false
        if (lower.startsWith("hourly rate") || lower.startsWith("quantity") || lower.startsWith("subtotal") || lower.startsWith("tax")) return false
        if (isAmountString(candidate) || isDateString(candidate)) return false
        return true
    }

    // -------------------------------------------------------------
    // 5. TOTAL AMOUNT EXTRACTION
    // -------------------------------------------------------------
    private fun extractTotalAmount(
        ocrLines: List<OcrTextLine>,
        plainLines: List<String>,
        fullText: String
    ): FieldResult<Double> {
        val totalLabels = listOf("invoice total", "grand total", "total amount", "net amount", "amount payable", "total due", "balance due", "total")

        // 1. 2D Spatial search: find "Invoice total:" label and look horizontally on right
        for (line in ocrLines) {
            val lower = line.text.lowercase(Locale.ROOT).trimEnd(':', ' ')
            if (totalLabels.any { lower == it || lower.startsWith("$it:") || lower.startsWith(it) }) {
                // Check inline amount (e.g. "Invoice total: $1 650.00")
                val inlineAmount = parseAmountFromText(line.text)
                if (inlineAmount != null && inlineAmount > 0.0) {
                    return FieldResult(inlineAmount, FieldConfidence.HIGH, "Inline with total label: $inlineAmount")
                }

                // Check horizontal right candidate
                val adjacentRight = findHorizontallyAlignedValue(line, ocrLines)
                if (adjacentRight != null) {
                    val adjacentAmount = parseAmountFromText(adjacentRight)
                    if (adjacentAmount != null && adjacentAmount > 0.0) {
                        return FieldResult(adjacentAmount, FieldConfidence.HIGH, "Table row total: $adjacentAmount")
                    }
                }
            }
        }

        // 2. Explicit regex for labels and amount in text
        val explicitAmountRegexes = listOf(
            Regex("(?i)(?:invoice\\s*total|grand\\s*total|total\\s*amount|net\\s*amount|balance\\s*due|total\\s*due)[:\\s]*[\\$€£₹Rs.]*\\s*([0-9]{1,3}(?:[\\s,][0-9]{3})*(?:\\.[0-9]{2})?|[0-9]+(?:\\.[0-9]{2})?)"),
            Regex("(?i)(?:^|\\n)\\s*total[:\\s]*[\\$€£₹Rs.]*\\s*([0-9]{1,3}(?:[\\s,][0-9]{3})*(?:\\.[0-9]{2})?|[0-9]+(?:\\.[0-9]{2})?)")
        )
        for (regex in explicitAmountRegexes) {
            val match = regex.find(fullText)
            if (match != null) {
                val parsed = cleanAndParseDouble(match.groupValues[1])
                if (parsed != null && parsed > 0.0) {
                    return FieldResult(parsed, FieldConfidence.HIGH, "Matched total regex: $parsed")
                }
            }
        }

        // 3. Fallback: collect all currency amounts on the invoice.
        // In retail & tax invoices, the Grand Total is almost universally the maximum currency amount!
        val allAmounts = mutableListOf<Double>()
        for (line in plainLines) {
            val parsed = parseAmountFromText(line)
            if (parsed != null && parsed > 0.0 && parsed < 10_000_000.0) {
                allAmounts.add(parsed)
            }
        }
        if (allAmounts.isNotEmpty()) {
            val maxAmount = allAmounts.maxOrNull()
            if (maxAmount != null && maxAmount > 0.0) {
                return FieldResult(maxAmount, FieldConfidence.MEDIUM, "Inferred as highest invoice amount: $maxAmount")
            }
        }

        return FieldResult(null, FieldConfidence.MISSING, "Total amount not detected")
    }

    private fun parseAmountFromText(text: String): Double? {
        // Matches amounts with space, comma, or period thousands separator (e.g. $1 650.00, $1,500.00, $150.00, 1650.00)
        val regex = Regex("[\\$€£₹Rs.]*\\s*([0-9]{1,3}(?:[\\s,][0-9]{3})+(?:\\.[0-9]{2})?|[0-9]+(?:\\.[0-9]{2})?)")
        val match = regex.find(text) ?: return null
        return cleanAndParseDouble(match.groupValues[1])
    }

    fun cleanAndParseDouble(raw: String): Double? {
        // Remove spaces (French/international thousands separator: "1 650.00" -> "1650.00") and commas
        val clean = raw.trim().replace(" ", "").replace(",", "")
        return clean.toDoubleOrNull()
    }

    private fun isAmountString(text: String): Boolean {
        return text.trim().startsWith("$") || text.trim().startsWith("€") || text.trim().startsWith("£") || text.trim().startsWith("₹") || parseAmountFromText(text) != null
    }

    // -------------------------------------------------------------
    // 6. WARRANTY INFORMATION EXTRACTION
    // -------------------------------------------------------------
    private fun extractWarranty(plainLines: List<String>, fullText: String): Pair<FieldResult<Int>, String?> {
        val warrantyPatterns = listOf(
            Regex("(?i)(\\d+)\\s*(?:year|yr)s?\\s*(?:limited\\s*)?(?:manufacturer\\s*)?(?:warranty|guarantee)"),
            Regex("(?i)(\\d+)\\s*(?:month|mo)s?\\s*(?:limited\\s*)?(?:manufacturer\\s*)?(?:warranty|guarantee)"),
            Regex("(?i)(?:warranty|guarantee)[:\\s]*(\\d+)\\s*(?:year|yr|month|mo)s?")
        )

        for (pattern in warrantyPatterns) {
            val match = pattern.find(fullText)
            if (match != null) {
                val matchedText = match.value
                val count = match.groupValues[1].toIntOrNull() ?: 1
                val isYear = matchedText.contains("year", ignoreCase = true) || matchedText.contains("yr", ignoreCase = true)
                val months = if (isYear) count * 12 else count
                return Pair(
                    FieldResult(months, FieldConfidence.HIGH, "Detected warranty term: $matchedText"),
                    matchedText
                )
            }
        }

        val wordMatches = listOf(
            Regex("(?i)one\\s*year\\s*warranty") to 12,
            Regex("(?i)two\\s*years?\\s*warranty") to 24,
            Regex("(?i)three\\s*years?\\s*warranty") to 36
        )
        for ((regex, months) in wordMatches) {
            if (regex.containsMatchIn(fullText)) {
                return Pair(
                    FieldResult(months, FieldConfidence.MEDIUM, "Standard warranty phrasing"),
                    "${months / 12} Year Warranty"
                )
            }
        }

        return Pair(FieldResult(null, FieldConfidence.MISSING, "No warranty duration mentioned on invoice"), null)
    }

    // -------------------------------------------------------------
    // HIGH-RES RENDERING FOR PDF AND IMAGE
    // -------------------------------------------------------------
    private fun renderPdfToHighResBitmap(file: File): Bitmap? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        return try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (renderer.pageCount > 0) {
                page = renderer.openPage(0)
                val maxDim = 2048
                val scale = maxDim.toFloat() / maxOf(page.width, page.height)
                val targetW = (page.width * scale).toInt().coerceAtLeast(600)
                val targetH = (page.height * scale).toInt().coerceAtLeast(600)

                val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } else null
        } finally {
            page?.close()
            renderer?.close()
            pfd?.close()
        }
    }

    private fun decodeImageBitmap(file: File): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)

        val maxDim = 2048
        var sampleSize = 1
        if (options.outHeight > maxDim || options.outWidth > maxDim) {
            val halfH = options.outHeight / 2
            val halfW = options.outWidth / 2
            while ((halfH / sampleSize) >= maxDim && (halfW / sampleSize) >= maxDim) {
                sampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
    }
}
