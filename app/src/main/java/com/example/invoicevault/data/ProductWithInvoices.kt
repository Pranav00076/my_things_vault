package com.example.invoicevault.data

import androidx.room.Embedded
import androidx.room.Relation

/**
 * 1-to-Many relation model connecting a Product to all associated Invoices.
 */
data class ProductWithInvoices(
    @Embedded
    val product: Product,
    @Relation(
        parentColumn = "productId",
        entityColumn = "productId"
    )
    val invoices: List<Invoice> = emptyList()
)

/**
 * Relation model joining an Invoice back to its parent Product.
 */
data class InvoiceWithProduct(
    @Embedded
    val invoice: Invoice,
    @Relation(
        parentColumn = "productId",
        entityColumn = "productId"
    )
    val product: Product
)

