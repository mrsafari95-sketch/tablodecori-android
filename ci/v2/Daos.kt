package com.tablodecori.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MaterialDao {
    @Query("SELECT * FROM materials WHERE deleted = 0 ORDER BY category, createdAt") fun observeAll(): Flow<List<MaterialEntity>>
    @Query("SELECT * FROM materials WHERE deleted = 0 ORDER BY category, createdAt") suspend fun getAll(): List<MaterialEntity>
    @Query("SELECT * FROM materials WHERE id = :id LIMIT 1") suspend fun get(id: String): MaterialEntity?
    @Upsert suspend fun upsert(entity: MaterialEntity)
    @Upsert suspend fun upsertAll(entities: List<MaterialEntity>)
    @Query("SELECT COUNT(*) FROM product_variables WHERE materialId = :materialId") suspend fun usageCount(materialId: String): Int
    @Query("UPDATE materials SET enabled = :enabled, updatedAt = :now WHERE id = :id") suspend fun setEnabled(id: String, enabled: Boolean, now: Long)
    @Query("UPDATE materials SET deleted = 1, enabled = 0, updatedAt = :now WHERE id = :id") suspend fun softDelete(id: String, now: Long)
}

@Dao
interface SizePriceDao {
    @Query("SELECT * FROM size_prices WHERE materialId = :materialId AND enabled = 1 ORDER BY widthCm, heightCm, pieceCount") fun observeFor(materialId: String): Flow<List<SizePriceEntity>>
    @Query("SELECT * FROM size_prices WHERE materialId = :materialId AND enabled = 1 ORDER BY widthCm, heightCm, pieceCount") suspend fun getFor(materialId: String): List<SizePriceEntity>
    @Query("SELECT * FROM size_prices WHERE enabled = 1") suspend fun getAll(): List<SizePriceEntity>
    @Upsert suspend fun upsert(entity: SizePriceEntity)
    @Query("DELETE FROM size_prices WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface ProductDao {
    @Transaction @Query("SELECT * FROM products WHERE deleted = 0 ORDER BY updatedAt DESC") fun observeAll(): Flow<List<ProductWithDetails>>
    @Transaction @Query("SELECT * FROM products WHERE deleted = 0 ORDER BY updatedAt DESC") suspend fun getAll(): List<ProductWithDetails>
    @Transaction @Query("SELECT * FROM products WHERE id = :id LIMIT 1") suspend fun get(id: String): ProductWithDetails?
    @Upsert suspend fun upsert(product: ProductEntity)
    @Insert suspend fun insertPieces(pieces: List<ProductPieceEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertVariables(items: List<ProductVariableEntity>)
    @Query("DELETE FROM product_pieces WHERE productId = :id") suspend fun deletePieces(id: String)
    @Query("DELETE FROM product_variables WHERE productId = :id") suspend fun deleteVariables(id: String)
    @Query("UPDATE products SET deleted = 1, active = 0, updatedAt = :now WHERE id = :id") suspend fun softDelete(id: String, now: Long)
    @Query("SELECT COUNT(DISTINCT productId) FROM product_variables WHERE materialId = :materialId") suspend fun affectedCount(materialId: String): Int
}

@Dao
interface ProfitRuleDao {
    @Query("SELECT * FROM profit_rules WHERE enabled = 1 ORDER BY pieceCount") fun observeAll(): Flow<List<ProfitRuleEntity>>
    @Query("SELECT * FROM profit_rules WHERE enabled = 1 ORDER BY pieceCount") suspend fun getAll(): List<ProfitRuleEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(rule: ProfitRuleEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(rules: List<ProfitRuleEntity>)
}

@Dao
interface OrderDao {
    @Transaction @Query("SELECT * FROM sent_orders ORDER BY dateEpochMillis DESC, createdAt DESC") fun observeAll(): Flow<List<OrderWithCosts>>
    @Transaction @Query("SELECT * FROM sent_orders ORDER BY dateEpochMillis DESC, createdAt DESC") suspend fun getAll(): List<OrderWithCosts>
    @Insert suspend fun insert(order: SentOrderEntity)
    @Insert suspend fun insertCosts(costs: List<OrderCostSnapshotEntity>)
    @Query("DELETE FROM sent_orders WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM price_change_history ORDER BY changedAt DESC") fun observeAll(): Flow<List<PriceChangeHistoryEntity>>
    @Query("SELECT * FROM price_change_history ORDER BY changedAt DESC") suspend fun getAll(): List<PriceChangeHistoryEntity>
    @Insert suspend fun insert(entity: PriceChangeHistoryEntity)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1") fun observe(): Flow<AppSettingsEntity?>
    @Query("SELECT * FROM app_settings WHERE id = 1") suspend fun get(): AppSettingsEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(settings: AppSettingsEntity)
}

@Dao
interface BackupDao {
    @Query("SELECT * FROM materials") suspend fun materials(): List<MaterialEntity>
    @Query("SELECT * FROM products") suspend fun products(): List<ProductEntity>
    @Query("SELECT * FROM size_prices") suspend fun sizePrices(): List<SizePriceEntity>
    @Query("SELECT * FROM product_pieces") suspend fun pieces(): List<ProductPieceEntity>
    @Query("SELECT * FROM product_variables") suspend fun variables(): List<ProductVariableEntity>
    @Query("SELECT * FROM profit_rules") suspend fun profits(): List<ProfitRuleEntity>
    @Query("SELECT * FROM sent_orders") suspend fun orders(): List<SentOrderEntity>
    @Query("SELECT * FROM order_cost_snapshots") suspend fun costs(): List<OrderCostSnapshotEntity>
    @Query("SELECT * FROM price_change_history") suspend fun history(): List<PriceChangeHistoryEntity>
    @Query("SELECT * FROM app_settings") suspend fun settings(): List<AppSettingsEntity>

    @Query("DELETE FROM order_cost_snapshots") suspend fun clearCosts()
    @Query("DELETE FROM sent_orders") suspend fun clearOrders()
    @Query("DELETE FROM price_change_history") suspend fun clearHistory()
    @Query("DELETE FROM product_variables") suspend fun clearVariables()
    @Query("DELETE FROM product_pieces") suspend fun clearPieces()
    @Query("DELETE FROM products") suspend fun clearProducts()
    @Query("DELETE FROM size_prices") suspend fun clearSizePrices()
    @Query("DELETE FROM materials") suspend fun clearMaterials()
    @Query("DELETE FROM profit_rules") suspend fun clearProfits()
    @Query("DELETE FROM app_settings") suspend fun clearSettings()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMaterials(v: List<MaterialEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putProducts(v: List<ProductEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSizePrices(v: List<SizePriceEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putPieces(v: List<ProductPieceEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putVariables(v: List<ProductVariableEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putProfits(v: List<ProfitRuleEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putOrders(v: List<SentOrderEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCosts(v: List<OrderCostSnapshotEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putHistory(v: List<PriceChangeHistoryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSettings(v: List<AppSettingsEntity>)
}
