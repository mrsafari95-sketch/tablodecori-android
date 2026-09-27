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

/** Money due at delivery is a promise, not money already received. */
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
)

/** The amount due at delivery is only received after collection is recorded. */
object OrderPaymentMath {
    fun received(quotedTotal: Long, deposit: Long, otherPaid: Long, codDue: Long, codCollected: Long): Long {
        require(listOf(quotedTotal, deposit, otherPaid, codDue, codCollected).all { it >= 0L }) { "مبالغ نمی‌توانند منفی باشند." }
        require(codCollected <= codDue) { "پرداخت دریافت‌شده درب منزل از مبلغ درب منزل بیشتر است." }
        val paid = Math.addExact(Math.addExact(deposit, otherPaid), codCollected)
        require(paid <= quotedTotal) { "دریافتی از مبلغ توافق‌شده بیشتر است." }
        require(deposit <= quotedTotal && otherPaid <= quotedTotal - deposit && codDue <= quotedTotal - deposit - otherPaid) { "مبلغ درب منزل از مانده سفارش بیشتر است." }
        return paid
    }
}

fun SentOrderEntity.outstandingToman(): Long = (quotedTotalToman - receivedToman).coerceAtLeast(0L)
fun SentOrderEntity.expectedProfitToman(): Long = quotedTotalToman - productCostSnapshotToman - shippingCostToman

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
    rateBasisPoints, wasteBasisPoints, enabled && !deleted, runCatching { SmartPackagingKind.valueOf(smartKind) }.getOrDefault(SmartPackagingKind.GENERIC)
)

fun ProductWithDetails.toModel(): ProductModel = ProductModel(
    product.id, product.name, product.active, product.manualProfitToman, product.profitMode, product.profitFormula, product.packagingSizeKey,
    pieces.sortedBy { it.sortOrder }.map { PieceInput(it.widthCm, it.heightCm, it.quantity) },
    materials.filter { !it.deleted }.map { it.id }.toSet()
)
