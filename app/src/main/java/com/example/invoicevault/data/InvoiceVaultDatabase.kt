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

@Database(entities = [Product::class], version = 2, exportSchema = true)
@TypeConverters(DatabaseConverters::class)
abstract class InvoiceVaultDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
}

/** Example non-destructive migration. Add every future schema change here; never enable destructive fallback. */
object InvoiceVaultMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_products_productName ON products(productName)")
        }
    }
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
