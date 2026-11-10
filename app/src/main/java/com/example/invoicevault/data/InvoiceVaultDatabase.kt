package com.example.invoicevault.data

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class DatabaseConverters {
    @TypeConverter fun warrantyUnitToString(value: WarrantyUnit): String = value.name
    @TypeConverter fun stringToWarrantyUnit(value: String): WarrantyUnit = WarrantyUnit.valueOf(value)
}

@Database(entities = [Product::class, Invoice::class], version = 3, exportSchema = true)
@TypeConverters(DatabaseConverters::class)
abstract class InvoiceVaultDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun invoiceDao(): InvoiceDao
}

/** Example non-destructive migration. Add every future schema change here; never enable destructive fallback. */
object InvoiceVaultMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_products_productName ON products(productName)")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `invoices` (
                    `invoiceId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `productId` INTEGER NOT NULL,
                    `invoiceNumber` TEXT NOT NULL,
                    `purchaseDate` TEXT NOT NULL,
                    `sellerName` TEXT NOT NULL,
                    `totalAmount` REAL NOT NULL,
                    `invoiceFileUri` TEXT NOT NULL,
                    `invoiceType` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    FOREIGN KEY(`productId`) REFERENCES `products`(`productId`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_invoices_productId` ON `invoices` (`productId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_invoices_invoiceNumber` ON `invoices` (`invoiceNumber`)")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
