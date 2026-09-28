package com.tablodecori.app.data

import androidx.room.withTransaction
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import android.util.Base64
import com.tablodecori.app.data.db.*
import com.tablodecori.app.pricing.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import java.util.UUID

class WorkshopRepository(private val db: AppDatabase, context: Context, private val engine: PricingEngine = PricingEngine()) {
    private val appContext = context.applicationContext
    private val photos = OrderPhotoStore(appContext)
    val materials: Flow<List<MaterialEntity>> = db.materialDao().observeAll()
    val products: Flow<List<ProductWithDetails>> = db.productDao().observeAll()
    val profitRules: Flow<List<ProfitRuleEntity>> = db.profitRuleDao().observeAll()
    val orders: Flow<List<OrderWithCosts>> = db.orderDao().observeAll()
    val history: Flow<List<PriceChangeHistoryEntity>> = db.historyDao().observeAll()
    val settings: Flow<AppSettingsEntity?> = db.settingsDao().observe()
    val stockItems: Flow<List<StockItemEntity>> = db.stockDao().observeAll()
    val expenses: Flow<List<ExpenseEntity>> = db.expenseDao().observeAll()
    val plannerTasks:Flow<List<PlannerTaskEntity>> = db.plannerDao().observeTasks()
    val plannerOccurrences:Flow<List<PlannerOccurrenceEntity>> = db.plannerDao().observeOccurrences()
    val plannerSettings:Flow<PlannerSettingsEntity?> = db.plannerDao().observeSettings()
    val plannerRewards:Flow<List<PlannerRewardEntity>> = db.plannerDao().observeRewards()
    val plannerXp:Flow<List<PlannerXpEntity>> = db.plannerDao().observeXp()
    val plannerRestDays:Flow<List<PlannerRestDayEntity>> = db.plannerDao().observeRestDays()
    fun sizePrices(materialId:String): Flow<List<SizePriceEntity>> = db.sizePriceDao().observeFor(materialId)
    suspend fun saveSizePrice(entity:SizePriceEntity){ require(entity.widthCm>0&&entity.heightCm>0&&entity.priceToman>=0){"ابعاد یا قیمت نامعتبر است."}; db.sizePriceDao().upsert(entity) }
    suspend fun deleteSizePrice(id:String)=db.sizePriceDao().disable(id,System.currentTimeMillis())

    private val plannerBackupTables=listOf("planner_tasks","planner_occurrences","planner_settings","planner_rewards","planner_xp_ledger","planner_rest_days")
    private val backupTables = listOf(
        "materials","size_prices","products","product_pieces","product_variables",
        "profit_rules","stock_items","sent_orders","order_stock_usage","order_cost_snapshots","price_change_history","app_settings","expenses"
    )+plannerBackupTables

    suspend fun exportFullBackup(): String {
        val sql=db.openHelper.readableDatabase
        val root=JSONObject().put("format","tablodecori-full-backup").put("version",5).put("createdAt",System.currentTimeMillis())
        val tables=JSONObject()
        backupTables.forEach { table ->
            val rows=JSONArray()
            sql.query("SELECT * FROM $table").use { c ->
                while(c.moveToNext()){
                    val row=JSONObject()
                    for(i in 0 until c.columnCount){
                        val name=c.getColumnName(i)
                        when(c.getType(i)){
                            Cursor.FIELD_TYPE_NULL -> row.put(name,JSONObject.NULL)
                            Cursor.FIELD_TYPE_INTEGER -> row.put(name,c.getLong(i))
                            Cursor.FIELD_TYPE_FLOAT -> row.put(name,c.getDouble(i))
                            Cursor.FIELD_TYPE_STRING -> row.put(name,c.getString(i))
                            Cursor.FIELD_TYPE_BLOB -> row.put(name,android.util.Base64.encodeToString(c.getBlob(i),android.util.Base64.NO_WRAP))
                        }
                    }
                    rows.put(row)
                }
            }
            tables.put(table,rows)
        }
        root.put("tables",tables)
        val images=JSONObject()
        (db.orderDao().getAll().map{it.order.photoFileName}+expenses.first().map{it.receiptFileName}).filter{it.isNotBlank()}.distinct().forEach { name ->
            val encoded=photos.exportBase64(name) ?: error("عکس یکی از سفارش‌ها برای بکاپ پیدا نشد: $name")
            images.put(name,encoded)
        }
        root.put("images",images)
        return root.toString(2)
    }

    suspend fun importFullBackup(json:String) {
        val root=JSONObject(json)
        val legacy=root.optString("format")=="tablodecori-backup"
        require(legacy || root.optString("format")=="tablodecori-full-backup"){"فایل بکاپ معتبر نیست."}
        val version=root.optInt("version",0)
        require(if(legacy)version==1 else version in 1..5){"نسخه فایل بکاپ پشتیبانی نمی‌شود."}
        val tables=if(legacy) JSONObject().apply {
            mapOf("materials" to "materials","products" to "products","pieces" to "product_pieces",
                "productVariables" to "product_variables","profitRules" to "profit_rules","orders" to "sent_orders",
                "orderCosts" to "order_cost_snapshots","priceHistory" to "price_change_history","settings" to "app_settings")
                .forEach { (old,new) -> put(new,root.getJSONArray(old)) }
            require(getJSONArray("materials").length()>0 && getJSONArray("app_settings").length()>0){"بکاپ قدیمی ناقص است."}
        } else root.optJSONObject("tables")?:error("اطلاعات بکاپ ناقص است.")
        val essential=setOf("materials","products","product_pieces","product_variables","profit_rules","sent_orders","order_cost_snapshots","price_change_history","app_settings")
        backupTables.filter { it in essential || (!legacy && version>=3 && it!="expenses" && it !in plannerBackupTables) || (!legacy && version>=4 && it=="expenses") || (!legacy && version>=5 && it in plannerBackupTables) }.forEach { require(tables.has(it)){"بکاپ ناقص است: $it"} }
        val images=if(!legacy && version>=2) root.optJSONObject("images")?:error("عکس‌های بکاپ ناقص است.") else JSONObject()
        val restoredImages=mutableMapOf<String,ByteArray>()
        var totalImageBytes=0L
        images.keys().forEach { name ->
            require(photos.file(name)!=null){"نام عکس در بکاپ نامعتبر است."}
            val bytes=Base64.decode(images.getString(name),Base64.DEFAULT)
            require(bytes.size<=8_000_000){"حجم یک عکس در بکاپ بیش از حد مجاز است."}
            totalImageBytes+=bytes.size
            require(totalImageBytes<=100_000_000){"حجم عکس‌های بکاپ بیش از حد مجاز است."}
            restoredImages[name]=bytes
        }
        if(!legacy && version>=2){
            val orders=tables.getJSONArray("sent_orders")
            for(i in 0 until orders.length()){
                val name=orders.getJSONObject(i).optString("photoFileName")
                require(name.isBlank() || restoredImages.containsKey(name)){"عکس یکی از سفارش‌ها در بکاپ موجود نیست."}
            }
        }
        if(!legacy && version>=4){
            val rows=tables.getJSONArray("expenses")
            for(i in 0 until rows.length()){
                val name=rows.getJSONObject(i).optString("receiptFileName")
                require(name.isBlank() || restoredImages.containsKey(name)){"عکس فیش یکی از هزینه‌ها در بکاپ موجود نیست."}
            }
        }
        restoredImages.forEach { (name,bytes) -> photos.restoreBytes(name,bytes) }
        val sql=db.openHelper.writableDatabase
        db.withTransaction {
            val b=db.backupDao()
            b.clearPlannerOccurrences();b.clearPlannerXp();b.clearPlannerRestDays();b.clearPlannerRewards();b.clearPlannerTasks();b.clearPlannerSettings()
            b.clearStockUsage();b.clearCosts();b.clearOrders();b.clearStockItems();b.clearExpenses();b.clearHistory();b.clearVariables();b.clearPieces();b.clearProducts()
            b.clearSizePrices();b.clearProfits();b.clearSettings();b.clearMaterials()
            backupTables.forEach { table ->
                if(!tables.has(table)) return@forEach
                val columns=mutableListOf<Pair<String,String>>()
                sql.query("PRAGMA table_info($table)").use { schema ->
                    while(schema.moveToNext()) columns+=schema.getString(1) to schema.getString(2)
                }
                val rows=tables.getJSONArray(table)
                for(r in 0 until rows.length()){
                    val row=rows.getJSONObject(r);val values=ContentValues()
                    row.keys().forEach { key ->
                        if(row.isNull(key)) values.putNull(key) else {
                            val value=row.get(key)
                            when(value){
                                is Int -> values.put(key,value)
                                is Long -> values.put(key,value)
                                is Double -> values.put(key,value)
                                is Boolean -> values.put(key,if(value)1 else 0)
                                else -> values.put(key,value.toString())
                            }
                        }
                    }
                    columns.forEach { (column,type) -> if(!row.has(column)) {
                        val fallback:Any=when(column){
                            "quotedTotalToman","otherPaidToman" -> if(table=="sent_orders")row.optLong("receivedToman",0L) else 0L
                            "formulaMode" -> "STANDARD"
                            "profitMode" -> "MANUAL"
                            "productType" -> "STANDARD"
                            "orderSource" -> "OTHER"
                            "frameColorOptions" -> "مشکی، سفید، طلایی، نقره‌ای، چوبی"
                            "shippingPayer" -> "SENDER"
                            "defaultShippingPayer" -> "RECIPIENT"
                            "defaultOrderStatus" -> "PREPARING"
                            "orderStatus" -> "SENT"
                            "lowStockPercent" -> 10
                            "tracked" -> 1
                            "enabled","active","suggestCodRemainder" -> 1
                            else -> if(type.contains("CHAR",true)||type.contains("TEXT",true))"" else 0L
                        }
                        when(fallback){is String->values.put(column,fallback);is Int->values.put(column,fallback);else->values.put(column,fallback as Long)}
                    } }
                    val result=sql.insert(table,SQLiteDatabase.CONFLICT_REPLACE,values)
                    require(result!=-1L){"بازیابی اطلاعات $table ناموفق بود."}
                }
            }
            sql.execSQL("UPDATE sent_orders SET codCollectedToman = codDueToman, receivedToman = MIN(quotedTotalToman, depositToman + otherPaidToman + codDueToman), actualProfitToman = MIN(quotedTotalToman, depositToman + otherPaidToman + codDueToman) - productCostSnapshotToman - CASE WHEN shippingPayer = 'SENDER' THEN shippingCostToman ELSE 0 END")
        }
        if(legacy || version<3) ensurePricingStructure()
        runCatching { ShippingReminder.refreshAll(appContext,db.orderDao().getAll().map{it.order}) }
        runCatching { PlannerScheduler.refreshAll(appContext,db.plannerDao().tasks(),db.plannerDao().settings()) }
    }

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
        material("inflation","تورم","OVERHEAD","PERCENT_OF_COST",0,100)

        // User-created materials are first-class data and must survive app restarts.

        val photoPrices=mapOf(
            "10x15" to 24000L,"13x18" to 42000L,"16x21" to 49000L,"20x30" to 88000L,"30x30" to 165000L,
            "30x40" to 190000L,"30x45" to 210000L,"30x50" to 216000L,"30x60" to 250000L,"30x70" to 345000L,
            "30x80" to 345000L,"40x60" to 440000L,"40x70" to 640000L,"40x80" to 900000L,"50x50" to 640000L,
            "50x70" to 640000L,"50x100" to 1200000L,"60x60" to 970000L,"60x90" to 970000L,"70x100" to 1250000L,
            "76x120" to 1500000L,"76x140" to 1780000L)
        val existingPhoto=db.sizePriceDao().getAllFor("photo_lab").associateBy{it.id}
        photoPrices.forEach{(key,defaultPrice)->val wh=key.split("x").map{it.toInt()};val id="photo_lab_$key";val old=existingPhoto[id];if(old==null) db.sizePriceDao().upsert(SizePriceEntity(id,"photo_lab",wh[0],wh[1],0,defaultPrice,true,now,now))}

        material("pack_foam","فوم بسته‌بندی","PACKAGING","PER_SET",0,0,"FOAM")
        material("pack_carton","کارتن بسته‌بندی","PACKAGING","PER_SET",0,0,"CARTON")
        material("pack_tape","چسب بسته‌بندی","PACKAGING","PER_SET",30000,0,"TAPE")
        material("pack_labor","دستمزد کارگر بسته‌بندی","PACKAGING","PER_SET",60000,0,"LABOR")
        val packs=listOf(Triple(40,60,70000L to 120000L),Triple(50,70,200000L to 130000L),Triple(60,90,200000L to 230000L),Triple(70,100,300000L to 230000L))
        for((w,h,costs) in packs){
            for((id,price) in listOf("pack_foam" to costs.first,"pack_carton" to costs.second,"pack_tape" to 30000L,"pack_labor" to 60000L)){
                val key=id+"_"+w+"x"+h
                val old=db.sizePriceDao().getAllFor(id).firstOrNull{it.id==key}
                if(old==null) db.sizePriceDao().upsert(SizePriceEntity(key,id,w,h,0,price,true,now,now))
            }
        }
    }

    suspend fun resetDefaults() {
        val now=System.currentTimeMillis()
        val defaults=mapOf("frame_pvc" to 110000L,"glass" to 330000L,"backboard_3mm" to 820000L,"frame_supplies" to 50000L,"production_labor" to 30000L,"pack_tape" to 30000L,"pack_labor" to 60000L)
        defaults.forEach{(id,p)->db.materialDao().get(id)?.let{db.materialDao().upsert(it.copy(priceToman=p,formulaMode="STANDARD",customFormula="",enabled=true,updatedAt=now))}}
        db.materialDao().get("unexpected_cost")?.let{db.materialDao().upsert(it.copy(rateBasisPoints=200,formulaMode="STANDARD",customFormula="",enabled=true,updatedAt=now))}
        db.materialDao().get("inflation")?.let{db.materialDao().upsert(it.copy(rateBasisPoints=100,formulaMode="STANDARD",customFormula="",enabled=true,updatedAt=now))}
        val photos=mapOf("10x15" to 24000L,"13x18" to 42000L,"16x21" to 49000L,"20x30" to 88000L,"30x30" to 165000L,"30x40" to 190000L,"30x45" to 210000L,"30x50" to 216000L,"30x60" to 250000L,"30x70" to 345000L,"30x80" to 345000L,"40x60" to 440000L,"40x70" to 640000L,"40x80" to 900000L,"50x50" to 640000L,"50x70" to 640000L,"50x100" to 1200000L,"60x60" to 970000L,"60x90" to 970000L,"70x100" to 1250000L,"76x120" to 1500000L,"76x140" to 1780000L)
        photos.forEach{(k,p)->val wh=k.split("x").map{it.toInt()};db.sizePriceDao().upsert(SizePriceEntity("photo_lab_$k","photo_lab",wh[0],wh[1],0,p,true,now,now))}
        listOf(Triple(40,60,70000L to 120000L),Triple(50,70,200000L to 130000L),Triple(60,90,200000L to 230000L),Triple(70,100,300000L to 230000L)).forEach{(w,h,c)->db.sizePriceDao().upsert(SizePriceEntity("pack_foam_"+w+"x"+h,"pack_foam",w,h,0,c.first,true,now,now));db.sizePriceDao().upsert(SizePriceEntity("pack_carton_"+w+"x"+h,"pack_carton",w,h,0,c.second,true,now,now));db.sizePriceDao().upsert(SizePriceEntity("pack_tape_"+w+"x"+h,"pack_tape",w,h,0,30000L,true,now,now));db.sizePriceDao().upsert(SizePriceEntity("pack_labor_"+w+"x"+h,"pack_labor",w,h,0,60000L,true,now,now))}
    }


    val pricedProducts: Flow<List<PricedProduct>> = combine(products, materials, profitRules, settings, db.sizePriceDao().observeAll()) { ps, ms, rs, st, sizeRules ->
        val pMaterials = ms.map { it.toPricing() }
        val profits = rs.associate { it.pieceCount to it.fixedToman }
        val rounding = st?.roundingStepToman ?: 10_000L
        ps.mapNotNull { rel ->
            val model = rel.toModel()
            runCatching { PricedProduct(model, engine.calculate(model.pieces, pMaterials, model.enabledMaterialIds, profits, rounding, ms.filter{it.formulaMode=="CUSTOM"}.associate{it.id to it.customFormula}, model.manualProfitToman.takeIf{model.profitMode=="MANUAL"}, model.profitFormula.takeIf{model.profitMode=="FORMULA"}.orEmpty(), sizeRules, model.packagingSizeKey, model.designMaterialsCostToman.takeIf{model.productType=="RELIEF"}?:0L)) }.getOrNull()
        }
    }

    suspend fun calculate(pieces: List<PieceInput>, enabledIds: Set<String>? = null, packagingSizeKey: String = "", profitFormula: String = "", manualProfitToman: Long = 0L, designMaterialsCostToman: Long = 0L): PricingResult {
        val ms = db.materialDao().getAll().map { it.toPricing() }
        val profits = db.profitRuleDao().getAll().associate { it.pieceCount to it.fixedToman }
        val st = db.settingsDao().get() ?: AppSettingsEntity(updatedAt = System.currentTimeMillis())
        val entities=db.materialDao().getAll()
        return engine.calculate(pieces, ms, enabledIds ?: ms.filter { it.enabled }.map { it.id }.toSet(), profits, st.roundingStepToman, entities.filter{it.formulaMode=="CUSTOM"}.associate{it.id to it.customFormula}, if(profitFormula.isBlank())manualProfitToman else null, profitFormula, db.sizePriceDao().getAll(), packagingSizeKey,designMaterialsCostToman)
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

    suspend fun saveProduct(id: String?, name: String, pieces: List<PieceInput>, enabledIds: Set<String>, active: Boolean = true, manualProfitToman: Long = 0L, profitMode: String = "MANUAL", profitFormula: String = "", packagingSizeKey: String = "", productType:String="STANDARD", designMaterialsCostToman:Long=0L): String {
        require(name.isNotBlank()) { "نام محصول الزامی است." }
        require(pieces.isNotEmpty() && pieces.all { it.widthCm > 0 && it.heightCm > 0 && it.quantity > 0 }) { "ابعاد و تعداد باید بزرگ‌تر از صفر باشند." }
        require(manualProfitToman >= 0) { "سود دستی نمی‌تواند منفی باشد." }
        require(productType in setOf("STANDARD","RELIEF") && designMaterialsCostToman>=0L){"نوع یا هزینه محصول نامعتبر است."}
        if(productType=="RELIEF")require(enabledIds.all{it in setOf("frame_pvc","packaging_bundle")}){"تابلو برجسته فقط قاب و بسته‌بندی دارد."}
        require(profitMode=="MANUAL" || profitMode=="FORMULA") { "روش محاسبه سود نامعتبر است." }
        if(profitMode=="FORMULA") require(profitFormula.isNotBlank()) { "فرمول سود خالی است." }
        val now = System.currentTimeMillis()
        val productId = id ?: UUID.randomUUID().toString()
        val old = id?.let { db.productDao().get(it)?.product }
        db.withTransaction {
            db.productDao().upsert(ProductEntity(id=productId,name=name,active=active,manualProfitToman=manualProfitToman,profitMode=profitMode,profitFormula=profitFormula,packagingSizeKey=packagingSizeKey,productType=productType,designMaterialsCostToman=if(productType=="RELIEF")designMaterialsCostToman else 0L,deleted=false,createdAt=old?.createdAt?:now,updatedAt=now))
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
        return saveProduct(null, "${m.name} - کپی", m.pieces, m.enabledMaterialIds, m.active, m.manualProfitToman, m.profitMode, m.profitFormula, m.packagingSizeKey,m.productType,m.designMaterialsCostToman)
    }

    suspend fun toggleProduct(id: String, active: Boolean) {
        val p = db.productDao().get(id)?.product ?: return
        db.productDao().upsert(p.copy(active = active, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteProduct(id: String) = db.productDao().softDelete(id, System.currentTimeMillis())

    private fun received(input: OrderFormInput): Long {
        require(input.shippingCostToman>=0){"هزینه ارسال نمی‌تواند منفی باشد."}
        require(input.shippingPayer in setOf("SENDER","RECIPIENT")){"روش پرداخت ارسال نامعتبر است."}
        require(input.orderStatus in setOf("PREPARING","READY","SENT")){"وضعیت سفارش نامعتبر است."}
        require(OrderSource.options.any{it.first==input.orderSource}){"منبع سفارش نامعتبر است."}
        require(input.plannedShipAtMillis>=0L){"تاریخ یادآوری نامعتبر است."}
        return OrderPaymentMath.received(input.quotedTotalToman,input.depositToman,input.otherPaidToman,input.codDueToman)
    }

    private suspend fun consumeStock(orderId:String, model:ProductModel, color:String) {
        val packageKey=com.tablodecori.app.pricing.PricingEngine.choosePackagingSize(model.pieces,db.sizePriceDao().getAll(),model.packagingSizeKey)
        val needs=StockPlanner.needs(model.pieces,model.enabledMaterialIds,db.materialDao().getAll(),color,packageKey)
        val now=System.currentTimeMillis()
        needs.forEach { need ->
            val old=db.stockDao().get(need.stockId)
            val item=old ?: StockItemEntity(need.stockId,need.materialId,need.materialName,need.variantKey,need.unit,updatedAt=now,tracked=false)
            db.stockDao().upsert(item.copy(materialName=need.materialName,onHandMicros=Math.subtractExact(item.onHandMicros,need.amountMicros),updatedAt=now))
        }
        db.stockDao().insertUsages(needs.map { OrderStockUsageEntity(orderId,it.stockId,it.amountMicros) })
    }

    private suspend fun releaseStock(orderId:String) {
        db.stockDao().usages(orderId).forEach { usage ->
            db.stockDao().get(usage.stockItemId)?.let { item ->
                db.stockDao().upsert(item.copy(onHandMicros=Math.addExact(item.onHandMicros,usage.amountMicros),updatedAt=System.currentTimeMillis()))
            }
        }
        db.stockDao().deleteUsages(orderId)
    }

    private suspend fun recolorFrameStock(orderId:String,color:String) {
        val newId="frame_pvc|${StockPlanner.colorKey(color)}"
        db.stockDao().usages(orderId).filter { it.stockItemId.startsWith("frame_pvc|") && it.stockItemId!=newId }.forEach { usage ->
            val old=db.stockDao().get(usage.stockItemId) ?: return@forEach
            val now=System.currentTimeMillis()
            db.stockDao().upsert(old.copy(onHandMicros=Math.addExact(old.onHandMicros,usage.amountMicros),updatedAt=now))
            val next=db.stockDao().get(newId) ?: StockItemEntity(newId,"frame_pvc",old.materialName,StockPlanner.colorKey(color),"METER",updatedAt=now)
            db.stockDao().upsert(next.copy(onHandMicros=Math.subtractExact(next.onHandMicros,usage.amountMicros),updatedAt=now))
            db.stockDao().deleteUsage(orderId,usage.stockItemId)
            db.stockDao().insertUsages(listOf(OrderStockUsageEntity(orderId,newId,usage.amountMicros)))
        }
    }

    suspend fun saveStock(id:String,onHandMicros:Long,targetMicros:Long) {
        require(targetMicros>0 && onHandMicros>=0){"موجودی فعلی باید صفر یا بیشتر و حد مطلوب بزرگ‌تر از صفر باشد."}
        db.withTransaction {
            val item=db.stockDao().get(id) ?: error("قلم انبار پیدا نشد.")
            db.stockDao().upsert(item.copy(onHandMicros=onHandMicros,targetMicros=targetMicros,notifiedLow=false,updatedAt=System.currentTimeMillis(),tracked=true))
        }
        checkStockAlerts()
    }

    suspend fun addStock(materialId:String,variantText:String,onHandMicros:Long,targetMicros:Long) {
        require(onHandMicros>=0L && targetMicros>0L){"موجودی یا حد مطلوب نامعتبر است."}
        val material=db.materialDao().get(materialId) ?: error("متریال پیدا نشد.")
        val unit=StockPlanner.unitFor(material) ?: error("این قلم موجودی فیزیکی ندارد.")
        val variant=StockPlanner.variantFor(materialId,variantText)
        val id="$materialId|$variant"
        db.withTransaction {
            require(db.stockDao().get(id)==null){"این قلم قبلاً ثبت شده؛ موجودی آن را ویرایش کنید."}
            db.stockDao().upsert(StockItemEntity(id,materialId,material.name,variant,unit,onHandMicros,targetMicros,updatedAt=System.currentTimeMillis()))
        }
        checkStockAlerts()
    }

    suspend fun trackStock(materialId:String,variantText:String) {
        val material=db.materialDao().get(materialId) ?: error("متریال پیدا نشد.")
        val unit=StockPlanner.unitFor(material) ?: error("این قلم موجودی فیزیکی ندارد.")
        val variant=StockPlanner.variantFor(materialId,variantText)
        val id="$materialId|$variant"
        db.withTransaction {
            val old=db.stockDao().get(id)
            val now=System.currentTimeMillis()
            db.stockDao().upsert(old?.copy(tracked=true,notifiedLow=false,updatedAt=now)
                ?: StockItemEntity(id,materialId,material.name,variant,unit,updatedAt=now,tracked=true))
        }
        checkStockAlerts()
    }

    suspend fun hideStock(id:String) {
        db.withTransaction {
            val old=db.stockDao().get(id) ?: return@withTransaction
            db.stockDao().upsert(old.copy(tracked=false,notifiedLow=false,updatedAt=System.currentTimeMillis()))
        }
    }

    suspend fun updateStockThreshold(percent:Int) {
        require(percent in 1..100){"آستانه هشدار باید بین ۱ تا ۱۰۰ درصد باشد."}
        val old=db.settingsDao().get() ?: AppSettingsEntity(updatedAt=System.currentTimeMillis())
        db.settingsDao().upsert(old.copy(lowStockPercent=percent,updatedAt=System.currentTimeMillis()))
        checkStockAlerts()
    }

    private suspend fun checkStockAlerts() {
        val threshold=db.settingsDao().get()?.lowStockPercent ?: 10
        db.stockDao().getAll().forEach { item ->
            if(!item.tracked) return@forEach
            val low=item.targetMicros>0L && item.onHandMicros <= item.targetMicros * threshold / 100L
            if(low && !item.notifiedLow) {
                if(StockAlert.show(appContext,item)) db.stockDao().upsert(item.copy(notifiedLow=true))
            } else if(!low && item.notifiedLow) db.stockDao().upsert(item.copy(notifiedLow=false))
        }
    }
    suspend fun refreshStockAlerts() = checkStockAlerts()

    suspend fun saveExpense(id:String?,dateMillis:Long,category:String,title:String,payeeName:String,amountToman:Long,note:String,receiptUri:String?):String {
        require(category in setOf("MATERIAL","PAYROLL","OTHER")){"دسته‌بندی هزینه نامعتبر است."}
        require(title.isNotBlank() && amountToman>0L && dateMillis>0L){"عنوان، تاریخ و مبلغ معتبر لازم است."}
        val old=id?.let{db.expenseDao().get(it)}
        val photo=receiptUri?.takeIf{it.isNotBlank()}?.let{photos.importSelected(it)} ?: old?.receiptFileName.orEmpty()
        val now=System.currentTimeMillis()
        val expense=ExpenseEntity(id?:UUID.randomUUID().toString(),dateMillis,category,title.trim(),payeeName.trim(),amountToman,note.trim(),photo,old?.createdAt?:now,now)
        try { db.expenseDao().upsert(expense) } catch(t:Throwable){if(photo!=old?.receiptFileName)photos.delete(photo);throw t}
        if(old!=null && old.receiptFileName.isNotBlank() && old.receiptFileName!=photo)photos.delete(old.receiptFileName)
        return expense.id
    }

    suspend fun deleteExpense(id:String) {
        val item=db.expenseDao().get(id)?:return
        db.expenseDao().delete(id)
        if(item.receiptFileName.isNotBlank())photos.delete(item.receiptFileName)
    }

    suspend fun updateOpeningCash(amount:Long){
        val old=db.settingsDao().get()?:AppSettingsEntity(updatedAt=System.currentTimeMillis())
        db.settingsDao().upsert(old.copy(openingCashToman=amount,updatedAt=System.currentTimeMillis()))
    }

    suspend fun savePlannerTask(input:PlannerTaskEntity):String {
        require(input.title.isNotBlank() && input.plannedAtMillis>0L){"عنوان و زمان کار لازم است."}
        require(input.priority in 1..3 && input.durationMinutes in 0..1440 && input.reminderMinutes in 0..1440){"تنظیمات کار نامعتبر است."}
        require(input.recurrence in setOf("NONE","DAILY","WEEKLY","MONTHLY")){"تکرار کار نامعتبر است."}
        val old=input.id.takeIf{it.isNotBlank()}?.let{db.plannerDao().task(it)}
        val now=System.currentTimeMillis()
        val task=input.copy(id=input.id.ifBlank{UUID.randomUUID().toString()},title=input.title.trim(),createdAt=old?.createdAt?:now,updatedAt=now)
        db.plannerDao().upsertTask(task)
        PlannerScheduler.schedule(appContext,task,db.plannerDao().settings())
        return task.id
    }

    suspend fun deletePlannerTask(id:String){
        db.plannerDao().deactivateTask(id,System.currentTimeMillis())
        PlannerScheduler.cancel(appContext,id)
    }

    suspend fun setPlannerStatus(id:String,dayMillis:Long,status:String,reason:String=""){
        require(status in setOf("DONE","SKIPPED","DEFERRED")){"وضعیت کار نامعتبر است."}
        val task=db.plannerDao().task(id)?:error("کار پیدا نشد.")
        require(PlannerEngine.due(task,dayMillis)){"این کار برای روز انتخابی نیست."}
        require(PlannerEngine.dateKey(dayMillis)<=PlannerEngine.dateKey(System.currentTimeMillis())){"کار آینده را نمی‌توان انجام‌شده ثبت کرد."}
        val day=PlannerEngine.dateKey(dayMillis)
        val now=System.currentTimeMillis()
        var deferred:PlannerTaskEntity?=null
        db.withTransaction {
            val dao=db.plannerDao()
            val previous=dao.occurrence(id,day)
            if(previous?.status=="DONE" && status!="DONE"){
                val oldXp=dao.xpFor(id,day).sumOf{it.amount}
                if(oldXp>0)dao.addXp(PlannerXpEntity(UUID.randomUUID().toString(),id,day,-oldXp,"برگرداندن وضعیت",now))
            }
            if(status=="DONE" && previous?.status!="DONE"){
                val due=PlannerEngine.occurrenceTime(task,dayMillis)
                val desired=PlannerEngine.xpForCompletion(task.priority,now<=due+3600000L,task.createdAt>due,PlannerEngine.dateKey(now)>day)
                val earned=dao.xpForDay(day).sumOf{it.amount}.coerceAtLeast(0)
                val grant=desired.coerceAtMost((100-earned).coerceAtLeast(0))
                if(grant>0)dao.addXp(PlannerXpEntity(UUID.randomUUID().toString(),id,day,grant,"انجام کار",now))
            }
            dao.upsertOccurrence(PlannerOccurrenceEntity(id,day,status,reason.trim(),now,previous?.focusMinutes?:0))
            if(status=="DEFERRED" && previous?.status!="DEFERRED"){
                val next=java.util.Calendar.getInstance().apply{timeInMillis=PlannerEngine.occurrenceTime(task,dayMillis);add(java.util.Calendar.DAY_OF_MONTH,1)}.timeInMillis
                if(!PlannerEngine.due(task,next)){
                    deferred=task.copy(id=UUID.randomUUID().toString(),plannedAtMillis=next,recurrence="NONE",weekDays="",createdAt=now,updatedAt=now)
                    dao.upsertTask(deferred!!)
                }
            }
        }
        PlannerScheduler.schedule(appContext,task,db.plannerDao().settings(),maxOf(now,PlannerEngine.occurrenceTime(task,dayMillis)+1L))
        deferred?.let{PlannerScheduler.schedule(appContext,it,db.plannerDao().settings())}
    }

    suspend fun addPlannerFocus(id:String,dayMillis:Long,minutes:Int){
        require(minutes in 1..120){"مدت تمرکز نامعتبر است."}
        val day=PlannerEngine.dateKey(dayMillis)
        val old=db.plannerDao().occurrence(id,day)
        db.plannerDao().upsertOccurrence(old?.copy(focusMinutes=old.focusMinutes+minutes,changedAt=System.currentTimeMillis())
            ?:PlannerOccurrenceEntity(id,day,"PENDING",changedAt=System.currentTimeMillis(),focusMinutes=minutes))
    }

    suspend fun setPlannerRestDay(day:Int,rest:Boolean){
        if(rest)db.plannerDao().upsertRestDay(PlannerRestDayEntity(day)) else db.plannerDao().deleteRestDay(day)
    }

    suspend fun savePlannerSettings(settings:PlannerSettingsEntity){
        require(settings.quietStartHour in 0..23 && settings.quietEndHour in 0..23 && settings.dailyNotificationLimit in 1..20 && settings.morningBriefHour in 0..23 && settings.eveningReviewHour in 0..23){"تنظیمات یادآوری نامعتبر است."}
        db.plannerDao().upsertSettings(settings.copy(updatedAt=System.currentTimeMillis()))
        PlannerScheduler.refreshAll(appContext,db.plannerDao().tasks(),settings)
    }

    suspend fun addPlannerReward(title:String,cost:Int){
        require(title.isNotBlank() && cost in 1..100000){"پاداش یا امتیاز نامعتبر است."}
        db.plannerDao().upsertReward(PlannerRewardEntity(UUID.randomUUID().toString(),title.trim(),cost,createdAt=System.currentTimeMillis()))
    }

    suspend fun redeemPlannerReward(id:String){
        val reward=db.plannerDao().reward(id)?:error("پاداش پیدا نشد.")
        require(reward.redeemedAt==0L){"این پاداش قبلاً دریافت شده است."}
        val xp=plannerXp.first().sumOf{it.amount}
        val used=plannerRewards.first().filter{it.redeemedAt>0L}.sumOf{it.xpCost}
        require(xp-used>=reward.xpCost){"امتیاز برای این پاداش کافی نیست."}
        db.plannerDao().upsertReward(reward.copy(redeemedAt=System.currentTimeMillis()))
    }

    suspend fun createOrder(input: OrderFormInput): String {
        require(input.customerName.isNotBlank()) { "نام گیرنده الزامی است." }
        val paid=received(input)
        val rel = db.productDao().get(input.productId) ?: error("محصول پیدا نشد.")
        val model = rel.toModel()
        val pricing = calculate(model.pieces, model.enabledMaterialIds, model.packagingSizeKey,designMaterialsCostToman=model.designMaterialsCostToman.takeIf{model.productType=="RELIEF"}?:0L)
        val id = UUID.randomUUID().toString()
        val internal = "TD-${System.currentTimeMillis().toString().takeLast(7)}"
        val actual = OrderMath.actualProfit(paid, pricing.costBeforeProfitToman, if(input.shippingPayer=="SENDER") input.shippingCostToman else 0L)
        val composition = model.pieces.joinToString(" + ") { "${it.quantity}× ${it.widthCm}×${it.heightCm}" }
        val photoName=input.selectedPhotoUri?.let{photos.importSelected(it)}.orEmpty()
        try { db.withTransaction {
            db.orderDao().insert(SentOrderEntity(
                id = id,
                internalNumber = internal,
                dateEpochMillis = input.dateEpochMillis,
                customerName = input.customerName,
                instagramId = input.instagramId,
                phone = input.phone,
                province = input.province,
                city = input.city,
                productId = input.productId,
                productNameSnapshot = model.name,
                compositionSnapshot = composition,
                pieceCountSnapshot = pricing.pieceCount,
                productCostSnapshotToman = pricing.costBeforeProfitToman,
                shippingCostToman = input.shippingCostToman,
                receivedToman = paid,
                actualProfitToman = actual,
                note = input.note,
                createdAt = System.currentTimeMillis(),
                addressDetails = input.addressDetails,
                postalCode = input.postalCode,
                frameColor = input.frameColor,
                quotedTotalToman = input.quotedTotalToman,
                depositToman = input.depositToman,
                otherPaidToman = input.otherPaidToman,
                codDueToman = input.codDueToman,
                codCollectedToman = input.codDueToman,
                photoFileName = photoName,
                shippingPayer = input.shippingPayer,
                dimensionsText = input.dimensionsText,
                plannedShipAtMillis = input.plannedShipAtMillis,
                orderStatus = input.orderStatus,
                trackingCode = input.trackingCode,
                orderSource = input.orderSource,
            ))
            db.orderDao().insertCosts(pricing.lines.map { OrderCostSnapshotEntity(orderId=id,materialId=it.materialId,name=it.name,category=it.category.name,amountToman=it.amountToman) })
            consumeStock(id,model,input.frameColor)
        }} catch(t:Throwable){ photos.delete(photoName); throw t }
        runCatching { ShippingReminder.schedule(appContext,id,input.plannedShipAtMillis,input.orderStatus) }
        runCatching { checkStockAlerts() }
        return id
    }

    suspend fun updateOrder(orderId: String, input: OrderFormInput) {
        require(input.customerName.isNotBlank()) { "نام گیرنده الزامی است." }
        val paid=received(input)
        val existing = db.orderDao().getAll().firstOrNull { it.order.id == orderId }?.order
            ?: error("سفارش پیدا نشد.")
        val replacement=if(input.productId!=existing.productId){
            val rel=db.productDao().get(input.productId)?:error("ست جدید پیدا نشد.")
            val model=rel.toModel()
            model to calculate(model.pieces,model.enabledMaterialIds,model.packagingSizeKey,designMaterialsCostToman=model.designMaterialsCostToman.takeIf{model.productType=="RELIEF"}?:0L)
        }else null
        val newCost=replacement?.second?.costBeforeProfitToman?:existing.productCostSnapshotToman
        val actual = OrderMath.actualProfit(paid, newCost, if(input.shippingPayer=="SENDER") input.shippingCostToman else 0L)
        val newPhoto=input.selectedPhotoUri?.let{photos.importSelected(it)}
        val photoName=when { newPhoto!=null->newPhoto; input.removePhoto->""; else->existing.photoFileName }
        try { db.withTransaction {
            if(replacement!=null) releaseStock(orderId)
            else if(input.frameColor.trim()!=existing.frameColor.trim()) recolorFrameStock(orderId,input.frameColor)
            db.orderDao().update(existing.copy(
            dateEpochMillis = input.dateEpochMillis,
            customerName = input.customerName,
            instagramId = input.instagramId,
            phone = input.phone,
            province = input.province,
            city = input.city,
            addressDetails = input.addressDetails,
            postalCode = input.postalCode,
            shippingCostToman = input.shippingCostToman,
            receivedToman = paid,
            actualProfitToman = actual,
            note = input.note,
            frameColor = input.frameColor,
            quotedTotalToman = input.quotedTotalToman,
            depositToman = input.depositToman,
            otherPaidToman = input.otherPaidToman,
            codDueToman = input.codDueToman,
            codCollectedToman = input.codDueToman,
            photoFileName = photoName,
            productId = if(replacement!=null) input.productId else existing.productId,
            productNameSnapshot = replacement?.first?.name?:existing.productNameSnapshot,
            compositionSnapshot = replacement?.first?.pieces?.joinToString(" + ") { "${it.quantity}× ${it.widthCm}×${it.heightCm}" }?:existing.compositionSnapshot,
            pieceCountSnapshot = replacement?.second?.pieceCount?:existing.pieceCountSnapshot,
            productCostSnapshotToman = newCost,
            shippingPayer = input.shippingPayer,
            dimensionsText = input.dimensionsText,
            plannedShipAtMillis = input.plannedShipAtMillis,
            orderStatus = input.orderStatus,
            trackingCode = input.trackingCode,
            orderSource = input.orderSource,
        ))
            if(replacement!=null){
                db.orderDao().deleteCosts(orderId)
                db.orderDao().insertCosts(replacement.second.lines.map { OrderCostSnapshotEntity(orderId=orderId,materialId=it.materialId,name=it.name,category=it.category.name,amountToman=it.amountToman) })
            }
            if(replacement!=null) consumeStock(orderId,replacement.first,input.frameColor)
        } } catch(t:Throwable){ if(newPhoto!=null) photos.delete(newPhoto); throw t }
        if(photoName!=existing.photoFileName) photos.delete(existing.photoFileName)
        runCatching { ShippingReminder.schedule(appContext,orderId,input.plannedShipAtMillis,input.orderStatus) }
        runCatching { checkStockAlerts() }
    }

    suspend fun deleteOrder(orderId: String) {
        val photo=db.orderDao().getAll().firstOrNull{it.order.id==orderId}?.order?.photoFileName.orEmpty()
        db.withTransaction { releaseStock(orderId);db.orderDao().delete(orderId) }
        photos.delete(photo)
        runCatching { ShippingReminder.cancel(appContext,orderId) }
        runCatching { checkStockAlerts() }
    }

    suspend fun updateSettings(rounding: Long, shipping: Long, dark: Boolean) {
        require(rounding > 0 && shipping >= 0) { "تنظیمات مبلغ نامعتبر است." }
        val old=db.settingsDao().get() ?: AppSettingsEntity(updatedAt=System.currentTimeMillis())
        db.settingsDao().upsert(old.copy(roundingStepToman=rounding,shippingDefaultToman=shipping,darkMode=dark,updatedAt=System.currentTimeMillis()))
    }

    suspend fun updateOrderPreferences(depositPercent:Int, defaultFrameColor:String, frameColorOptions:String, suggestCodRemainder:Boolean, defaultShippingPayer:String, defaultOrderStatus:String){
        require(depositPercent in 0..100){"درصد بیعانه باید بین صفر تا صد باشد."}
        require(defaultShippingPayer in setOf("RECIPIENT","SENDER") && defaultOrderStatus in setOf("PREPARING","READY","SENT")){"پیش‌فرض سفارش نامعتبر است."}
        val old=db.settingsDao().get() ?: AppSettingsEntity(updatedAt=System.currentTimeMillis())
        db.settingsDao().upsert(old.copy(defaultDepositPercent=depositPercent,defaultFrameColor=defaultFrameColor.trim(),frameColorOptions=frameColorOptions.trim(),suggestCodRemainder=suggestCodRemainder,defaultShippingPayer=defaultShippingPayer,defaultOrderStatus=defaultOrderStatus,updatedAt=System.currentTimeMillis()))
    }

    suspend fun updateProfitRule(pieceCount: Int, amount: Long) {
        require(pieceCount > 0 && amount >= 0)
        db.profitRuleDao().upsert(ProfitRuleEntity(pieceCount, fixedToman=amount, updatedAt=System.currentTimeMillis()))
    }
}
