package com.example.invoicevault.ocr

enum class FieldConfidence {
    HIGH,     // Directly matched with high-confidence pattern / keyword
    MEDIUM,   // Inferred through heuristics or positional rules
    LOW,      // Fallback candidate
    MISSING   // Not found in OCR text
}

data class FieldResult<T>(
    val value: T?,
    val confidence: FieldConfidence = FieldConfidence.MISSING,
    val explanation: String = ""
)

/**
 * Encapsulates all extracted data parsed from an invoice via Google ML Kit Text Recognition.
 */
data class ExtractedInvoiceData(
    val invoiceNumber: FieldResult<String> = FieldResult(null, FieldConfidence.MISSING),
    val purchaseDate: FieldResult<String> = FieldResult(null, FieldConfidence.MISSING), // ISO-8601 yyyy-MM-dd
    val sellerName: FieldResult<String> = FieldResult(null, FieldConfidence.MISSING),
    val productName: FieldResult<String> = FieldResult(null, FieldConfidence.MISSING),
    val totalAmount: FieldResult<Double> = FieldResult(null, FieldConfidence.MISSING),
    val warrantyDurationMonths: FieldResult<Int> = FieldResult(null, FieldConfidence.MISSING),
    val warrantyDescription: String? = null,
    val rawExtractedText: String = "",
    val detectedLines: List<String> = emptyList()
) {
    /**
     * Checks if all mandatory fields (invoice number, date, seller, amount) were detected.
     */
    val hasAllCrucialFields: Boolean
        get() = invoiceNumber.value != null &&
                purchaseDate.value != null &&
                sellerName.value != null &&
                totalAmount.value != null

    /**
     * Count of successfully extracted fields.
     */
    val extractedFieldCount: Int
        get() = listOf(
            invoiceNumber.value,
            purchaseDate.value,
            sellerName.value,
            productName.value,
            totalAmount.value,
            warrantyDurationMonths.value
        ).count { it != null }
}

