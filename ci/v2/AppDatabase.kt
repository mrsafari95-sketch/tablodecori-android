package com.tablodecori.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MaterialEntity::class, ProductEntity::class, ProductPieceEntity::class, ProductVariableEntity::class,
        ProfitRuleEntity::class, SentOrderEntity::class, OrderCostSnapshotEntity::class, PriceChangeHistoryEntity::class, AppSettingsEntity::class, SizePriceEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun materialDao(): MaterialDao
    abstract fun productDao(): ProductDao
    abstract fun sizePriceDao(): SizePriceDao
    abstract fun profitRuleDao(): ProfitRuleDao
    abstract fun orderDao(): OrderDao
    abstract fun historyDao(): HistoryDao
    abstract fun settingsDao(): SettingsDao
    abstract fun backupDao(): BackupDao

    companion object {
        private val MIGRATION_2_3 = object : Migration(2,3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE materials ADD COLUMN formulaMode TEXT NOT NULL DEFAULT 'STANDARD'")
                db.execSQL("ALTER TABLE materials ADD COLUMN customFormula TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE products ADD COLUMN manualProfitToman INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE products ADD COLUMN profitMode TEXT NOT NULL DEFAULT 'MANUAL'")
                db.execSQL("ALTER TABLE products ADD COLUMN profitFormula TEXT NOT NULL DEFAULT ''")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3,4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS size_prices (id TEXT NOT NULL PRIMARY KEY, materialId TEXT NOT NULL, widthCm INTEGER NOT NULL, heightCm INTEGER NOT NULL, pieceCount INTEGER NOT NULL DEFAULT 0, priceToman INTEGER NOT NULL DEFAULT 0, enabled INTEGER NOT NULL DEFAULT 1, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_size_prices_materialId ON size_prices(materialId)")
            }
        }
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "tablodecori.db")
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration(true)
                .build()
    }
}
