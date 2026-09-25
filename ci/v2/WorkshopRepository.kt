package com.tablodecori.app.data

import androidx.room.withTransaction
import com.tablodecori.app.data.db.*
import com.tablodecori.app.pricing.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID

class WorkshopRepository(private val db: AppDatabase, private val engine: PricingEngine = PricingEngine()) {
    val materials: Flow<List<MaterialEntity>> = db.materialDao().observeAll()
    val products: Flow<List<ProductWithDetails>> = db.productDao().observeAll()
    val profitRules: Flow<List<ProfitRuleEntity>> = db.profitRuleDao().observeAll()
    val orders: Flow<List<OrderWithCosts>> = db.orderDao().observeAll()
    val history: Flow<List<PriceChangeHistoryEntity>> = db.historyDao().observeAll()
    val settings: Flow<AppSettingsEntity?> = db.settingsDao().observe()

    suspend fun ensurePhotoPrices() {
        val sizes = listOf("10x15","13x18","16x21","20x30","30x30","30x40","30x45","30x50","30x60","30x70","30x80","40x60","40x70","40x80","50x50","50x70","50x100","60x60","60x90","70x70","70x100","76x120","76x140")
        val now = System.currentTimeMillis()
        sizes.forEach { key ->
            val id = "photo_$key"
            if (db.materialDao().get(id) == null) {
                db.materialDao().upsert(MaterialEntity(id=id, name="عکس " + key.replace("x","×"), category="PRODUCTION", calculationType="PER_PIECE", priceToman=0L, enabled=true, createdAt=now, updatedAt=now))
            }
        }
    }

    val pricedProducts: Flow<List<PricedProduct>> = combine(products, materials, profitRules, settings) { ps, ms, rs, st ->
        val pMaterials = ms.map { it.toPricing() }
        val profits = rs.associate { it.pieceCount to it.fixedToman }
        val rounding = st?.roundingStepToman ?: 10_000L
        ps.mapNotNull { rel ->
            val model = rel.toModel()
            runCatching { PricedProduct(model, engine.calculate(model.pieces, pMaterials, model.enabledMaterialIds, profits, rounding, ms.filter{it.formulaMode=="CUSTOM"}.associate{it.id to it.customFormula}, model.manualProfitToman.takeIf{model.profitMode=="MANUAL"}, model.profitFormula.takeIf{model.profitMode=="FORMULA"}.orEmpty())) }.getOrNull()
        }
    }

    suspend fun calculate(pieces: List<PieceInput>, enabledIds: Set<String>? = null): PricingResult {
        val ms = db.materialDao().getAll().map { it.toPricing() }
        val profits = db.profitRuleDao().getAll().associate { it.pieceCount to it.fixedToman }
        val st = db.settingsDao().get() ?: AppSettingsEntity(updatedAt = System.currentTimeMillis())
        val entities=db.materialDao().getAll()
        return engine.calculate(pieces, ms, enabledIds ?: ms.filter { it.enabled }.map { it.id }.toSet(), profits, st.roundingStepToman, entities.filter{it.formulaMode=="CUSTOM"}.associate{it.id to it.customFormula}, 0L, "")
    }

    suspend fun saveMaterial(entity: MaterialEntity): Int {
        require(entity.name.isNotBlank()) { "نام متغیر الزامی است." }
        require(entity.priceToman >= 0 && entity.rateBasisPoints in 0..100_000 && entity.wasteBasisPoints in 0..100_000) { "قیمت یا درصد نامعتبر است." }
        if(entity.formulaMode=="CUSTOM") FormulaEvaluator.evaluate(entity.customFormula,mapOf("width" to java.math.BigDecimal(40),"height" to java.math.BigDecimal(60),"qty" to java.math.BigDecimal.ONE,"unitPrice" to java.math.BigDecimal(entity.priceToman),"area" to java.math.BigDecimal("0.24"),"perimeter" to java.math.BigDecimal("2.2"),"count" to java.math.BigDecimal.ONE,"subtotal" to java.math.BigDecimal(100000)))
        val old = db.materialDao().get(entity.id)
        val affected = if (old != null) db.productDao().affectedCount(entity.id) else 0
        db.withTransaction {
            db.materialDao().upsert(entity.copy(updatedAt = System.currentTimeMillis()))
            if (old != null && (old.priceToman != entity.priceToman || old.rateBasisPoints != entity.rateBasisPoints)) {
                db.historyDao().insert(PriceChangeHistoryEntity(
                    materialId = entity.id, materialName = entity.name,
                    oldPriceToman = old.priceToman, newPriceToman = entity.priceToman,
                    oldRateBasisPoints = old.rateBasisPoints, newRateBasisPoints = entity.rateBasisPoints,
                    changedAt = System.currentTimeMillis(), affectedProductCount = affected
                ))
            }
        }
        return affected
    }

    suspend fun toggleMaterial(id: String, enabled: Boolean) {
        db.materialDao().get(id)?.let { db.materialDao().upsert(it.copy(enabled = enabled, updatedAt = System.currentTimeMillis())) }
    }

    suspend fun deleteMaterial(id: String): Int {
        val usage = db.materialDao().usageCount(id)
        db.materialDao().softDelete(id, System.currentTimeMillis())
        return usage
    }

    suspend fun saveProduct(id: String?, name: String, pieces: List<PieceInput>, enabledIds: Set<String>, active: Boolean = true, manualProfitToman: Long = 0L, profitMode: String = "MANUAL", profitFormula: String = ""): String {
        require(name.isNotBlank()) { "نام محصول الزامی است." }
        require(pieces.isNotEmpty() && pieces.all { it.widthCm > 0 && it.heightCm > 0 && it.quantity > 0 }) { "ابعاد و تعداد باید بزرگ‌تر از صفر باشند." }
        require(manualProfitToman >= 0) { "سود دستی نمی‌تواند منفی باشد." }
        require(profitMode=="MANUAL" || profitMode=="FORMULA") { "روش محاسبه سود نامعتبر است." }
        if(profitMode=="FORMULA") require(profitFormula.isNotBlank()) { "فرمول سود خالی است." }
        val now = System.currentTimeMillis()
        val productId = id ?: UUID.randomUUID().toString()
        val old = id?.let { db.productDao().get(it)?.product }
        db.withTransaction {
            db.productDao().upsert(ProductEntity(id=productId,name=name,active=active,manualProfitToman=manualProfitToman,profitMode=profitMode,profitFormula=profitFormula,deleted=false,createdAt=old?.createdAt?:now,updatedAt=now))
            db.productDao().deletePieces(productId)
            db.productDao().deleteVariables(productId)
            db.productDao().insertPieces(pieces.mapIndexed { i, p -> ProductPieceEntity(productId=productId,widthCm=p.widthCm,heightCm=p.heightCm,quantity=p.quantity,sortOrder=i) })
            db.productDao().insertVariables(enabledIds.map { ProductVariableEntity(productId, it, true) })
        }
        return productId
    }

    suspend fun duplicateProduct(id: String): String? {
        val p = db.productDao().get(id) ?: return null
        val m = p.toModel()
        return saveProduct(null, "${m.name} - کپی", m.pieces, m.enabledMaterialIds, m.active, m.manualProfitToman, m.profitMode, m.profitFormula)
    }

    suspend fun toggleProduct(id: String, active: Boolean) {
        val p = db.productDao().get(id)?.product ?: return
        db.productDao().upsert(p.copy(active = active, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteProduct(id: String) = db.productDao().softDelete(id, System.currentTimeMillis())

    suspend fun createOrder(
        productId: String, dateEpochMillis: Long, customer: String, instagram: String, phone: String,
        province: String, city: String, shipping: Long, received: Long, note: String
    ): String {
        require(customer.isNotBlank()) { "نام گیرنده الزامی است." }
        require(shipping >= 0 && received >= 0) { "مبالغ نمی‌توانند منفی باشند." }
        val rel = db.productDao().get(productId) ?: error("محصول پیدا نشد.")
        val model = rel.toModel()
        val pricing = calculate(model.pieces, model.enabledMaterialIds)
        val id = UUID.randomUUID().toString()
        val internal = "TD-${System.currentTimeMillis().toString().takeLast(7)}"
        val actual = OrderMath.actualProfit(received, pricing.costBeforeProfitToman, shipping)
        val composition = model.pieces.joinToString(" + ") { "${it.quantity}× ${it.widthCm}×${it.heightCm}" }
        db.withTransaction {
            db.orderDao().insert(SentOrderEntity(id, internal, dateEpochMillis, customer, instagram, phone, province, city, productId,
                model.name, composition, pricing.pieceCount, pricing.costBeforeProfitToman, shipping, received, actual, note, System.currentTimeMillis()))
            db.orderDao().insertCosts(pricing.lines.map { OrderCostSnapshotEntity(orderId=id,materialId=it.materialId,name=it.name,category=it.category.name,amountToman=it.amountToman) })
        }
        return id
    }

    suspend fun updateSettings(rounding: Long, shipping: Long, dark: Boolean) {
        require(rounding > 0 && shipping >= 0) { "تنظیمات مبلغ نامعتبر است." }
        db.settingsDao().upsert(AppSettingsEntity(roundingStepToman=rounding,shippingDefaultToman=shipping,darkMode=dark,updatedAt=System.currentTimeMillis()))
    }

    suspend fun updateProfitRule(pieceCount: Int, amount: Long) {
        require(pieceCount > 0 && amount >= 0)
        db.profitRuleDao().upsert(ProfitRuleEntity(pieceCount, fixedToman=amount, updatedAt=System.currentTimeMillis()))
    }
}
