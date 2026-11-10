package com.example.invoicevault

import android.app.Application
import androidx.room.Room
import com.example.invoicevault.data.InvoiceVaultDatabase
import com.example.invoicevault.data.ProductRepository
import com.example.invoicevault.data.InvoiceVaultMigrations

class InvoiceVaultApp : Application() {
    private val database by lazy {
        Room.databaseBuilder(this, InvoiceVaultDatabase::class.java, "invoice-vault.db")
            .addMigrations(*InvoiceVaultMigrations.ALL)
            .build()
    }
    val productRepository by lazy { ProductRepository(database.productDao()) }
}
