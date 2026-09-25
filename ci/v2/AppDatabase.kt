package com.tablodecori.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MaterialEntity::class, ProductEntity::class, ProductPieceEntity::class, ProductVariableEntity::class,
        ProfitRuleEntity::class, SentOrderEntity::class, OrderCostSnapshotEntity::class, PriceChangeHistoryEntity::class, AppSettingsEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun materialDao(): MaterialDao
    abstract fun productDao(): ProductDao
    abstract fun profitRuleDao(): ProfitRuleDao
    abstract fun orderDao(): OrderDao
    abstract fun historyDao(): HistoryDao
    abstract fun settingsDao(): SettingsDao
    abstract fun backupDao(): BackupDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "tablodecori.db")
                .fallbackToDestructiveMigration(true)
                .build()
    }
}
