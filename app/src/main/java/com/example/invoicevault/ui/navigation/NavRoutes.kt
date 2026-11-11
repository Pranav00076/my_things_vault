package com.example.invoicevault.ui.navigation

import android.net.Uri

object NavRoutes {
    const val INVOICE_DASHBOARD = "invoice_dashboard"
    const val UPLOAD_INVOICE = "upload_invoice"
    const val CAMERA_SCANNER = "camera_scanner"
    const val INVOICE_HISTORY = "invoice_history"
    const val PRODUCTS_CATALOG = "products_catalog"

    const val INVOICE_PREVIEW = "invoice_preview/{filePath}"
    fun invoicePreview(filePath: String): String =
        "invoice_preview/${Uri.encode(filePath)}"

    const val OCR_REVIEW = "ocr_review/{filePath}"
    fun ocrReview(filePath: String): String =
        "ocr_review/${Uri.encode(filePath)}"

    const val INVOICE_DETAILS = "invoice_details/{invoiceId}"
    fun invoiceDetails(invoiceId: Long): String =
        "invoice_details/$invoiceId"

    const val EDIT_INVOICE = "edit_invoice/{invoiceId}"
    fun editInvoice(invoiceId: Long): String =
        "edit_invoice/$invoiceId"
}

