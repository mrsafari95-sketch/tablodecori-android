package com.tablodecori.app.data.db

import androidx.room.*

@Entity(tableName = "materials")
data class MaterialEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val calculationType: String,
    val priceToman: Long,
    val rateBasisPoints: Int = 0,
    val wasteBasisPoints: Int = 0,
    val enabled: Boolean = true,
    val smartKind: String = "GENERIC",
    val formulaMode: String = "STANDARD",
    val customFormula: String = "",
    val deleted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "products", indices = [Index("name")])
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val active: Boolean = true,
    val manualProfitToman: Long = 0L,
    val profitMode: String = "MANUAL",
    val profitFormula: String = "",
    val deleted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "product_pieces",
    foreignKeys = [ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("productId")]
)
data class ProductPieceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: String,
    val widthCm: Int,
    val heightCm: Int,
    val quantity: Int,
    val sortOrder: Int,
)

@Entity(
    tableName = "product_variables",
    primaryKeys = ["productId", "materialId"],
    foreignKeys = [
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MaterialEntity::class, parentColumns = ["id"], childColumns = ["materialId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("materialId")]
)
data class ProductVariableEntity(val productId: String, val materialId: String, val enabled: Boolean = true)

@Entity(tableName = "profit_rules")
data class ProfitRuleEntity(
    @PrimaryKey val pieceCount: Int,
    val ruleType: String = "BASED_ON_PIECE_COUNT",
    val fixedToman: Long,
    val enabled: Boolean = true,
    val updatedAt: Long,
)

@Entity(tableName = "sent_orders", indices = [Index("dateEpochMillis"), Index("productId")])
data class SentOrderEntity(
    @PrimaryKey val id: String,
    val internalNumber: String,
    val dateEpochMillis: Long,
    val customerName: String,
    val instagramId: String,
    val phone: String,
    val province: String,
    val city: String,
    val productId: String?,
    val productNameSnapshot: String,
    val compositionSnapshot: String,
    val pieceCountSnapshot: Int,
    val productCostSnapshotToman: Long,
    val shippingCostToman: Long,
    val receivedToman: Long,
    val actualProfitToman: Long,
    val note: String,
    val createdAt: Long,
)

@Entity(
    tableName = "order_cost_snapshots",
    foreignKeys = [ForeignKey(entity = SentOrderEntity::class, parentColumns = ["id"], childColumns = ["orderId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("orderId")]
)
data class OrderCostSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: String,
    val materialId: String?,
    val name: String,
    val category: String,
    val amountToman: Long,
)

@Entity(
    tableName = "price_change_history",
    foreignKeys = [ForeignKey(entity = MaterialEntity::class, parentColumns = ["id"], childColumns = ["materialId"], onDelete = ForeignKey.NO_ACTION)],
    indices = [Index("materialId"), Index("changedAt")]
)
data class PriceChangeHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val materialId: String,
    val materialName: String,
    val oldPriceToman: Long,
    val newPriceToman: Long,
    val oldRateBasisPoints: Int,
    val newRateBasisPoints: Int,
    val changedAt: Long,
    val affectedProductCount: Int,
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val roundingStepToman: Long = 10_000L,
    val shippingDefaultToman: Long = 0L,
    val darkMode: Boolean = false,
    val updatedAt: Long,
)

data class ProductWithDetails(
    @Embedded val product: ProductEntity,
    @Relation(parentColumn = "id", entityColumn = "productId") val pieces: List<ProductPieceEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(ProductVariableEntity::class, parentColumn = "productId", entityColumn = "materialId")
    ) val materials: List<MaterialEntity>,
)

data class OrderWithCosts(
    @Embedded val order: SentOrderEntity,
    @Relation(parentColumn = "id", entityColumn = "orderId") val costs: List<OrderCostSnapshotEntity>,
)
