package com.tablodecori.app.data

import com.tablodecori.app.data.db.*
import com.tablodecori.app.pricing.*

data class ProductModel(
    val id: String,
    val name: String,
    val active: Boolean,
    val manualProfitToman: Long,
    val profitMode: String,
    val profitFormula: String,
    val packagingSizeKey: String,
    val pieces: List<PieceInput>,
    val enabledMaterialIds: Set<String>,
)

data class PricedProduct(val product: ProductModel, val pricing: PricingResult)

data class MonthlySummary(
    val orderCount: Int = 0,
    val pieceCount: Int = 0,
    val salesToman: Long = 0,
    val costToman: Long = 0,
    val shippingToman: Long = 0,
    val profitToman: Long = 0,
    val lossToman: Long = 0,
    val netToman: Long = 0,
)

fun MaterialEntity.toPricing(): MaterialInput = MaterialInput(
    id, name, MaterialCategory.valueOf(category), CalculationType.valueOf(calculationType), priceToman,
    rateBasisPoints, wasteBasisPoints, enabled && !deleted, runCatching { SmartPackagingKind.valueOf(smartKind) }.getOrDefault(SmartPackagingKind.GENERIC)
)

fun ProductWithDetails.toModel(): ProductModel = ProductModel(
    product.id, product.name, product.active, product.manualProfitToman, product.profitMode, product.profitFormula, product.packagingSizeKey,
    pieces.sortedBy { it.sortOrder }.map { PieceInput(it.widthCm, it.heightCm, it.quantity) },
    materials.filter { !it.deleted }.map { it.id }.toSet()
)
