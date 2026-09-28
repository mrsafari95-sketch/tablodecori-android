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
    val productType: String,
    val designMaterialsCostToman: Long,
    val pieces: List<PieceInput>,
    val enabledMaterialIds: Set<String>,
)

data class PricedProduct(val product: ProductModel, val pricing: PricingResult)

/** The workshop treats payment at the door as an immediate transfer. */
data class OrderFormInput(
    val productId: String,
    val dateEpochMillis: Long,
    val customerName: String,
    val instagramId: String,
    val phone: String,
    val province: String,
    val city: String,
    val addressDetails: String,
    val postalCode: String,
    val shippingCostToman: Long,
    val quotedTotalToman: Long,
    val depositToman: Long,
    val otherPaidToman: Long,
    val codDueToman: Long,
    val codCollectedToman: Long,
    val frameColor: String,
    val note: String,
    val selectedPhotoUri: String? = null,
    val removePhoto: Boolean = false,
    val shippingPayer: String = "RECIPIENT",
    val dimensionsText: String = "",
    val plannedShipAtMillis: Long = 0L,
    val orderStatus: String = "PREPARING",
    val trackingCode: String = "",
    val orderSource: String = "OTHER",
)

object OrderSource {
    val options=listOf("INSTAGRAM" to "اینستاگرام","BASALAM" to "باسلام","TELEGRAM" to "تلگرام","WEBSITE" to "سایت","OTHER" to "سایر")
    fun label(key:String)=options.firstOrNull{it.first==key}?.second?:"سایر"
}

object OrderPaymentMath {
    fun received(quotedTotal: Long, deposit: Long, otherPaid: Long, codDue: Long): Long {
        require(listOf(quotedTotal, deposit, otherPaid, codDue).all { it >= 0L }) { "مبالغ نمی‌توانند منفی باشند." }
        val paid = Math.addExact(Math.addExact(deposit, otherPaid), codDue)
        require(paid <= quotedTotal) { "دریافتی از مبلغ توافق‌شده بیشتر است." }
        return paid
    }
}

fun SentOrderEntity.outstandingToman(): Long = (quotedTotalToman - receivedToman).coerceAtLeast(0L)
fun SentOrderEntity.sellerShippingToman(): Long = if(shippingPayer=="SENDER") shippingCostToman else 0L
fun SentOrderEntity.expectedProfitToman(): Long = quotedTotalToman - productCostSnapshotToman - sellerShippingToman()
fun SentOrderEntity.displayDimensions(): String = dimensionsText.ifBlank { compositionSnapshot }

data class MonthlySummary(
    val orderCount: Int = 0,
    val pieceCount: Int = 0,
    val salesToman: Long = 0,
    val costToman: Long = 0,
    val shippingToman: Long = 0,
    val profitToman: Long = 0,
    val lossToman: Long = 0,
    val netToman: Long = 0,
    val receivedToman: Long = 0,
    val outstandingToman: Long = 0,
    val expectedProfitToman: Long = 0,
)

fun MaterialEntity.toPricing(): MaterialInput = MaterialInput(
    id, name, MaterialCategory.valueOf(category), CalculationType.valueOf(calculationType), priceToman,
    rateBasisPoints, wasteBasisPoints, enabled && !deleted && smartKind!=MaterialCatalog.STOCK_ONLY, runCatching { SmartPackagingKind.valueOf(smartKind) }.getOrDefault(SmartPackagingKind.GENERIC)
)

fun ProductWithDetails.toModel(): ProductModel = ProductModel(
    product.id, product.name, product.active, product.manualProfitToman, product.profitMode, product.profitFormula, product.packagingSizeKey, product.productType, product.designMaterialsCostToman,
    pieces.sortedBy { it.sortOrder }.map { PieceInput(it.widthCm, it.heightCm, it.quantity) },
    MaterialCatalog.selectedCanonicalIds(materials.filter { !it.deleted }.map { it.id }.toSet())
)
