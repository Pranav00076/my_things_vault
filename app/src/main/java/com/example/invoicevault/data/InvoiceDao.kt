package com.example.invoicevault.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InvoiceDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(invoice: Invoice): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(invoices: List<Invoice>): List<Long>

    @Update
    suspend fun update(invoice: Invoice): Int

    @Delete
    suspend fun delete(invoice: Invoice): Int

    @Query("DELETE FROM invoices WHERE invoiceId = :invoiceId")
    suspend fun deleteById(invoiceId: Long): Int

    @Query("DELETE FROM invoices WHERE productId = :productId")
    suspend fun deleteByProductId(productId: Long): Int

    @Query("SELECT * FROM invoices WHERE invoiceId = :invoiceId LIMIT 1")
    fun observeById(invoiceId: Long): Flow<Invoice?>

    @Query("SELECT * FROM invoices WHERE invoiceId = :invoiceId LIMIT 1")
    suspend fun getById(invoiceId: Long): Invoice?

    @Query("SELECT * FROM invoices WHERE productId = :productId ORDER BY purchaseDate DESC, invoiceId DESC")
    fun observeInvoicesForProduct(productId: Long): Flow<List<Invoice>>

    @Query("SELECT * FROM invoices WHERE productId = :productId ORDER BY purchaseDate DESC, invoiceId DESC")
    suspend fun getInvoicesForProduct(productId: Long): List<Invoice>

    @Query("SELECT * FROM invoices ORDER BY purchaseDate DESC, invoiceId DESC")
    fun observeAll(): Flow<List<Invoice>>

    @Query("SELECT COUNT(*) FROM invoices")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM invoices WHERE productId = :productId")
    fun observeCountForProduct(productId: Long): Flow<Int>

    @Query(
        """
        SELECT * FROM invoices 
        WHERE invoiceNumber LIKE '%' || :query || '%' 
           OR sellerName LIKE '%' || :query || '%'
        ORDER BY purchaseDate DESC, invoiceId DESC
        """
    )
    fun searchInvoices(query: String): Flow<List<Invoice>>

    @Transaction
    @Query("SELECT * FROM products WHERE productId = :productId LIMIT 1")
    fun observeProductWithInvoices(productId: Long): Flow<ProductWithInvoices?>

    @Transaction
    @Query("SELECT * FROM invoices WHERE invoiceId = :invoiceId LIMIT 1")
    fun observeInvoiceWithProduct(invoiceId: Long): Flow<InvoiceWithProduct?>

    @Transaction
    @Query("SELECT * FROM products ORDER BY purchaseDate DESC, productId DESC")
    fun observeAllProductsWithInvoices(): Flow<List<ProductWithInvoices>>
}

