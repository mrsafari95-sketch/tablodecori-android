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
    fun sizePrices(materialId:String): Flow<List<SizePriceEntity>> = db.sizePriceDao().observeFor(materialId)
    suspend fun saveSizePrice(entity:SizePriceEntity){ require(entity.widthCm>0&&entity.heightCm>0&&entity.priceToman>=0){"ابعاد یا قیمت نامعتبر است."}; db.sizePriceDao().upsert(entity) }
    suspend fun deleteSizePrice(id:String)=db.sizePriceDao().delete(id)

    suspend fun ensurePricingStructure() {
        val now=System.currentTimeMillis()
        suspend fun material(id:String,name:String,category:String,type:String,price:Long=0L,rate:Int=0,kind:String="GENERIC"){
            val old=db.materialDao().get(id)
            db.materialDao().upsert(MaterialEntity(id=id,name=name,category=category,calculationType=type,priceToman=old?.priceToman?:price,rateBasisPoints=old?.rateBasisPoints?:rate,wasteBasisPoints=old?.wasteBasisPoints?:0,enabled=old?.enabled?:true,smartKind=kind,deleted=false,createdAt=old?.createdAt?:now,updatedAt=now,formulaMode=old?.formulaMode?:"STANDARD",customFormula=old?.customFormula?:""))
        }
        material("frame_pvc","فریم PVC","PRODUCTION","PER_LINEAR_METER",110000)
        material("glass","شیشه","PRODUCTION","PER_SQUARE_METER",330000)
        material("backboard_3mm","شاسی ۳ میل","PRODUCTION","PER_SQUARE_METER",820000)
        material("frame_supplies","ملزومات قاب","PRODUCTION","PER_PIECE",50000)
        material("production_labor","دستمزد تولید هر قاب","PRODUCTION","PER_PIECE",30000)
        material("photo_lab","عکس لابراتوار","PRODUCTION","PER_SET",0,0,"PHOTO_TABLE")
        material("packaging_bundle","بسته‌بندی","PACKAGING","PER_SET",0,0,"PACKAGE_BUNDLE")
        material("unexpected_cost","هزینه پیش‌بینی نشده","OVERHEAD","PERCENT_OF_COST",0,200)
        material("inflation","تورم","OVERHEAD","PERCENT_OF_COST",0,200)

        val allowed=setOf("frame_pvc","glass","backboard_3mm","frame_supplies","production_labor","photo_lab","packaging_bundle","unexpected_cost","inflation","pack_foam","pack_carton","pack_tape","pack_labor")
        db.materialDao().getAll().filter{it.id !in allowed}.forEach{db.materialDao().softDelete(it.id,now)}

        val photoPrices=mapOf(
            "10x15" to 24000L,"13x18" to 42000L,"16x21" to 49000L,"20x30" to 88000L,"30x30" to 165000L,
            "30x40" to 190000L,"30x45" to 210000L,"30x50" to 216000L,"30x60" to 250000L,"30x70" to 345000L,
            "30x80" to 345000L,"40x60" to 440000L,"40x70" to 640000L,"40x80" to 900000L,"50x50" to 640000L,
            "50x70" to 640000L,"50x100" to 1200000L,"60x60" to 970000L,"60x90" to 970000L,"70x100" to 1250000L,
            "76x120" to 1500000L,"76x140" to 1780000L)
        val existingPhoto=db.sizePriceDao().getFor("photo_lab").associateBy{it.id}
        photoPrices.forEach{(key,defaultPrice)->val wh=key.split("x").map{it.toInt()};val id="photo_lab_$key";val old=existingPhoto[id];db.sizePriceDao().upsert(SizePriceEntity(id,"photo_lab",wh[0],wh[1],0,old?.priceToman?:defaultPrice,true,old?.createdAt?:now,now))}

        material("pack_foam","فوم بسته‌بندی","PACKAGING","PER_SET",0,0,"FOAM")
        material("pack_carton","کارتن بسته‌بندی","PACKAGING","PER_SET",0,0,"CARTON")
        material("pack_tape","چسب بسته‌بندی","PACKAGING","PER_SET",30000,0,"TAPE")
        material("pack_labor","دستمزد کارگر بسته‌بندی","PACKAGING","PER_SET",60000,0,"LABOR")
        val packs=listOf(Triple(40,60,70000L to 120000L),Triple(50,70,200000L to 130000L),Triple(60,90,200000L to 230000L),Triple(70,100,300000L to 230000L))
        for((w,h,costs) in packs){
            for((id,price) in listOf("pack_foam" to costs.first,"pack_carton" to costs.second)){
                val key=id+"_"+w+"x"+h
                val old=db.sizePriceDao().getFor(id).firstOrNull{it.id==key}
                db.sizePriceDao().upsert(SizePriceEntity(key,id,w,h,0,old?.priceToman?:price,true,old?.createdAt?:now,now))
            }
        }
    }

    suspend fun resetDefaults() {
        val now=System.currentTimeMillis()
        val defaults=mapOf("frame_pvc" to 110000L,"glass" to 330000L,"backboard_3mm" to 820000L,"frame_supplies" to 50000L,"production_labor" to 30000L,"pack_tape" to 30000L,"pack_labor" to 60000L)
        defaults.forEach{(id,p)->db.materialDao().get(id)?.let{db.materialDao().upsert(it.copy(priceToman=p,formulaMode="STANDARD",customFormula="",enabled=true,updatedAt=now))}}
        db.materialDao().get("unexpected_cost")?.let{db.materialDao().upsert(it.copy(rateBasisPoints=200,formulaMode="STANDARD",customFormula="",enabled=true,updatedAt=now))}
        db.materialDao().get("inflation")?.let{db.materialDao().upsert(it.copy(rateBasisPoints=200,formulaMode="STANDARD",customFormula="",enabled=true,updatedAt=now))}
        val photos=mapOf("10x15" to 24000L,"13x18" to 42000L,"16x21" to 49000L,"20x30" to 88000L,"30x30" to 165000L,"30x40" to 190000L,"30x45" to 210000L,"30x50" to 216000L,"30x60" to 250000L,"30x70" to 345000L,"30x80" to 345000L,"40x60" to 440000L,"40x70" to 640000L,"40x80" to 900000L,"50x50" to 640000L,"50x70" to 640000L,"50x100" to 1200000L,"60x60" to 970000L,"60x90" to 970000L,"70x100" to 1250000L,"76x120" to 1500000L,"76x140" to 1780000L)
        photos.forEach{(k,p)->val wh=k.split("x").map{it.toInt()};db.sizePriceDao().upsert(SizePriceEntity("photo_lab_$k","photo_lab",wh[0],wh[1],0,p,true,now,now))}
        listOf(Triple(40,60,70000L to 120000L),Triple(50,70,200000L to 130000L),Triple(60,90,200000L to 230000L),Triple(70,100,300000L to 230000L)).forEach{(w,h,c)->db.sizePriceDao().upsert(SizePriceEntity("pack_foam_"+w+"x"+h,"pack_foam",w,h,0,c.first,true,now,now));db.sizePriceDao().upsert(SizePriceEntity("pack_carton_"+w+"x"+h,"pack_carton",w,h,0,c.second,true,now,now))}
    }


    val pricedProducts: Flow<List<PricedProduct>> = combine(products, materials, profitRules, settings, db.sizePriceDao().observeAll()) { ps, ms, rs, st, sizeRules ->
        val pMaterials = ms.map { it.toPricing() }
        val profits = rs.associate { it.pieceCount to it.fixedToman }
        val rounding = st?.roundingStepToman ?: 10_000L
        ps.mapNotNull { rel ->
            val model = rel.toModel()
            runCatching { PricedProduct(model, engine.calculate(model.pieces, pMaterials, model.enabledMaterialIds, profits, rounding, ms.filter{it.formulaMode=="CUSTOM"}.associate{it.id to it.customFormula}, model.manualProfitToman.takeIf{model.profitMode=="MANUAL"}, model.profitFormula.takeIf{model.profitMode=="FORMULA"}.orEmpty(), sizeRules)) }.getOrNull()
        }
    }

    suspend fun calculate(pieces: List<PieceInput>, enabledIds: Set<String>? = null): PricingResult {
        val ms = db.materialDao().getAll().map { it.toPricing() }
        val profits = db.profitRuleDao().getAll().associate { it.pieceCount to it.fixedToman }
        val st = db.settingsDao().get() ?: AppSettingsEntity(updatedAt = System.currentTimeMillis())
        val entities=db.materialDao().getAll()
        return engine.calculate(pieces, ms, enabledIds ?: ms.filter { it.enabled }.map { it.id }.toSet(), profits, st.roundingStepToman, entities.filter{it.formulaMode=="CUSTOM"}.associate{it.id to it.customFormula}, 0L, "", db.sizePriceDao().getAll())
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
