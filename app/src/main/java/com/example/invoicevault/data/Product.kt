package com.example.invoicevault.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Shared product record. productId is the stable FK for invoices, warranties and service history.
 * Member 2 and 3 should use Long productId columns referencing products.productId.
 */
@Entity(tableName = "products", indices = [Index(value = ["productName"])])
data class Product(
    @PrimaryKey(autoGenerate = true) val productId: Long = 0,
    val productName: String,
    val brand: String,
    val category: String,
    /** ISO-8601 yyyy-MM-dd; stored as text to keep Room schema portable. */
    val purchaseDate: String,
    val purchasePrice: Double,
    val sellerName: String,
    val productDescription: String = "",
    /** Persist a content URI; request persistable read permission when importing an image. */
    val productImageUri: String? = null,
    val warrantyDuration: Int = 0,
    val warrantyUnit: WarrantyUnit = WarrantyUnit.MONTHS,
)

enum class WarrantyUnit { DAYS, MONTHS, YEARS }
