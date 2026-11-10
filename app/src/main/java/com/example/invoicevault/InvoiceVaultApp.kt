package com.example.invoicevault

import android.app.Application
import androidx.room.Room
import com.example.invoicevault.data.InvoiceRepository
import com.example.invoicevault.data.InvoiceVaultDatabase
import com.example.invoicevault.data.ProductRepository
import com.example.invoicevault.data.InvoiceVaultMigrations
import com.example.invoicevault.storage.InvoiceStorageManager

class InvoiceVaultApp : Application() {
    private val database by lazy {
        Room.databaseBuilder(this, InvoiceVaultDatabase::class.java, "invoice-vault.db")
            .addMigrations(*InvoiceVaultMigrations.ALL)
            .build()
    }
    val productRepository by lazy { ProductRepository(database.productDao()) }
    val invoiceRepository by lazy { InvoiceRepository(database.invoiceDao()) }
    val invoiceStorageManager by lazy { InvoiceStorageManager(this) }
}
