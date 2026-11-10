package com.example.invoicevault.data

import kotlinx.coroutines.flow.Flow

class ProductRepository(private val dao: ProductDao) {
    val products: Flow<List<Product>> = dao.observeAll()
    val productCount: Flow<Int> = dao.observeCount()
    fun observeProduct(productId: Long): Flow<Product?> = dao.observeById(productId)
    suspend fun addProduct(product: Product): Long = dao.insert(product)
    suspend fun updateProduct(product: Product): Boolean = dao.update(product) > 0
    suspend fun deleteProduct(product: Product): Boolean = dao.delete(product) > 0
    suspend fun deleteProduct(productId: Long): Boolean = dao.deleteById(productId) > 0
}
