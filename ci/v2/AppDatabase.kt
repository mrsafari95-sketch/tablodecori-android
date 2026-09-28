package com.tablodecori.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MaterialEntity::class, ProductEntity::class, ProductPieceEntity::class, ProductVariableEntity::class,
        ProfitRuleEntity::class, SentOrderEntity::class, OrderCostSnapshotEntity::class, PriceChangeHistoryEntity::class, AppSettingsEntity::class, SizePriceEntity::class, StockItemEntity::class, OrderStockUsageEntity::class, ExpenseEntity::class],
    version = 9,
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
    abstract fun stockDao(): StockDao
    abstract fun expenseDao(): ExpenseDao

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
        private val MIGRATION_4_5 = object : Migration(4,5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN packagingSizeKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE sent_orders ADD COLUMN addressDetails TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE sent_orders ADD COLUMN postalCode TEXT NOT NULL DEFAULT ''")
            }
        }
        private val MIGRATION_5_6 = object : Migration(5,6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sent_orders ADD COLUMN frameColor TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE sent_orders ADD COLUMN quotedTotalToman INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sent_orders ADD COLUMN depositToman INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sent_orders ADD COLUMN otherPaidToman INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sent_orders ADD COLUMN codDueToman INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sent_orders ADD COLUMN codCollectedToman INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sent_orders ADD COLUMN photoFileName TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE sent_orders SET quotedTotalToman = receivedToman, otherPaidToman = receivedToman")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN defaultDepositPercent INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN defaultFrameColor TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN frameColorOptions TEXT NOT NULL DEFAULT 'مشکی، سفید، طلایی، نقره‌ای، چوبی'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN suggestCodRemainder INTEGER NOT NULL DEFAULT 1")
            }
        }
        private val MIGRATION_6_7 = object : Migration(6,7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sent_orders ADD COLUMN shippingPayer TEXT NOT NULL DEFAULT 'SENDER'")
                db.execSQL("ALTER TABLE sent_orders ADD COLUMN dimensionsText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE sent_orders ADD COLUMN plannedShipAtMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sent_orders ADD COLUMN orderStatus TEXT NOT NULL DEFAULT 'SENT'")
                db.execSQL("ALTER TABLE sent_orders ADD COLUMN trackingCode TEXT NOT NULL DEFAULT ''")
            }
        }
        private val MIGRATION_7_8 = object : Migration(7,8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN lowStockPercent INTEGER NOT NULL DEFAULT 10")
                db.execSQL("CREATE TABLE IF NOT EXISTS stock_items (id TEXT NOT NULL PRIMARY KEY, materialId TEXT NOT NULL, materialName TEXT NOT NULL, variantKey TEXT NOT NULL, unit TEXT NOT NULL, onHandMicros INTEGER NOT NULL DEFAULT 0, targetMicros INTEGER NOT NULL DEFAULT 0, notifiedLow INTEGER NOT NULL DEFAULT 0, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_stock_items_materialId_variantKey ON stock_items(materialId, variantKey)")
                db.execSQL("CREATE TABLE IF NOT EXISTS order_stock_usage (orderId TEXT NOT NULL, stockItemId TEXT NOT NULL, amountMicros INTEGER NOT NULL, PRIMARY KEY(orderId, stockItemId), FOREIGN KEY(orderId) REFERENCES sent_orders(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(stockItemId) REFERENCES stock_items(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_order_stock_usage_stockItemId ON order_stock_usage(stockItemId)")
            }
        }
        private val MIGRATION_8_9 = object : Migration(8,9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN defaultShippingPayer TEXT NOT NULL DEFAULT 'RECIPIENT'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN defaultOrderStatus TEXT NOT NULL DEFAULT 'PREPARING'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN openingCashToman INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE products ADD COLUMN productType TEXT NOT NULL DEFAULT 'STANDARD'")
                db.execSQL("ALTER TABLE products ADD COLUMN designMaterialsCostToman INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sent_orders ADD COLUMN orderSource TEXT NOT NULL DEFAULT 'OTHER'")
                db.execSQL("ALTER TABLE stock_items ADD COLUMN tracked INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE TABLE IF NOT EXISTS expenses (id TEXT NOT NULL PRIMARY KEY, dateEpochMillis INTEGER NOT NULL, category TEXT NOT NULL, title TEXT NOT NULL, payeeName TEXT NOT NULL, amountToman INTEGER NOT NULL, note TEXT NOT NULL, receiptFileName TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_dateEpochMillis ON expenses(dateEpochMillis)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_category ON expenses(category)")
                db.execSQL("UPDATE sent_orders SET codCollectedToman = codDueToman, receivedToman = MIN(quotedTotalToman, depositToman + otherPaidToman + codDueToman), actualProfitToman = MIN(quotedTotalToman, depositToman + otherPaidToman + codDueToman) - productCostSnapshotToman - CASE WHEN shippingPayer = 'SENDER' THEN shippingCostToman ELSE 0 END")
            }
        }
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "tablodecori.db")
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .build()
    }
}
