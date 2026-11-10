package com.example.invoicevault.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Invoice record managed by Member 2.
 * Connected to Product via foreign key productId referencing products.productId.
 * Multiple invoices can be linked to a single product (e.g. original bill, repair bills, warranty slips).
 */
@Entity(
    tableName = "invoices",
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["productId"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["productId"]),
        Index(value = ["invoiceNumber"])
    ]
)
data class Invoice(
    @PrimaryKey(autoGenerate = true)
    val invoiceId: Long = 0,
    val productId: Long,
    val invoiceNumber: String,
    /** ISO-8601 yyyy-MM-dd; matches Product.purchaseDate schema standard. */
    val purchaseDate: String,
    val sellerName: String,
    val totalAmount: Double,
    /** Local file path or content URI for the stored invoice document (image or PDF). */
    val invoiceFileUri: String,
    /** Type of invoice, e.g. TAX_INVOICE, RECEIPT, WARRANTY_SLIP, PROFORMA. */
    val invoiceType: String = InvoiceTypes.TAX_INVOICE,
    /** Epoch timestamp in milliseconds when the invoice record was created. */
    val createdAt: Long = System.currentTimeMillis()
)

object InvoiceTypes {
    const val TAX_INVOICE = "TAX_INVOICE"
    const val RECEIPT = "RECEIPT"
    const val WARRANTY_SLIP = "WARRANTY_SLIP"
    const val REPAIR_BILL = "REPAIR_BILL"
    const val OTHER = "OTHER"

    val ALL = listOf(TAX_INVOICE, RECEIPT, WARRANTY_SLIP, REPAIR_BILL, OTHER)
}

