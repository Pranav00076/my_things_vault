package com.example.invoicevault.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(product: Product): Long

    @Update
    suspend fun update(product: Product): Int

    @Delete
    suspend fun delete(product: Product): Int

    @Query("DELETE FROM products WHERE productId = :productId")
    suspend fun deleteById(productId: Long): Int

    @Query("SELECT * FROM products WHERE productId = :productId LIMIT 1")
    fun observeById(productId: Long): Flow<Product?>

    @Query("SELECT * FROM products ORDER BY purchaseDate DESC, productId DESC")
    fun observeAll(): Flow<List<Product>>

    @Query("SELECT COUNT(*) FROM products")
    fun observeCount(): Flow<Int>
}
