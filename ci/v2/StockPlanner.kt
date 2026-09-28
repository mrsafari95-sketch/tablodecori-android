package com.tablodecori.app.data

import com.tablodecori.app.data.db.MaterialEntity
import com.tablodecori.app.pricing.PieceInput

data class StockNeed(val materialId:String, val materialName:String, val variantKey:String, val unit:String, val amountMicros:Long) {
    val stockId:String get()="$materialId|$variantKey"
}

/** A fixed, reviewable consumption snapshot is recorded for every new order. */
object StockPlanner {
    const val SCALE=1_000_000L
    fun sizeKey(width:Int,height:Int)="${minOf(width,height)}x${maxOf(width,height)}"
    fun colorKey(color:String)=color.trim().ifBlank { "بدون رنگ" }
    private fun isPhotoPrint(material:MaterialEntity)=material.id=="photo_lab" || material.id.startsWith("photo_") || material.name.contains("چاپ عکس") || material.name.contains("عکس لابراتوار") || material.name.trim()=="عکس"
    fun unitFor(material:MaterialEntity):String? {
        val id=material.id
        if(MaterialCatalog.isLegacy(id) || material.category=="OVERHEAD" || isPhotoPrint(material) || id=="packaging_bundle" || id.contains("labor")) return null
        return when(id){"backboard_3mm","pack_foam","pack_carton","pack_tape","frame_supplies"->"PIECE";"frame_pvc"->"METER";"glass"->"SQM"
            else->when(material.calculationType){"PER_PIECE","PER_SET","SMART_PACKAGING"->"PIECE";"PER_LINEAR_METER"->"METER";"PER_SQUARE_METER"->"SQM";else->null}}
    }
    fun variantFor(materialId:String,text:String):String {
        val value=text.trim().replace('×','x').replace('X','x').replace(' ','x')
        if(materialId in setOf("backboard_3mm","pack_foam","pack_carton")) {
            val parts=value.split("x").map { it.toIntOrNull() }
            require(parts.size==2 && parts.all { it!=null && it>0 }) { "ابعاد را مانند 50x70 وارد کنید." }
            return sizeKey(parts[0]!!,parts[1]!!)
        }
        if(materialId=="frame_pvc") { require(text.isNotBlank()) { "رنگ فریم را وارد کنید." };return text.trim() }
        return ""
    }

    fun needs(pieces:List<PieceInput>, enabledIds:Set<String>, materials:List<MaterialEntity>, frameColor:String, packagingSizeKey:String):List<StockNeed> {
        if(pieces.isEmpty()) return emptyList()
        val active=materials.filter { it.enabled && !it.deleted }.associateBy { it.id }
        val selectedIds=MaterialCatalog.selectedCanonicalIds(enabledIds)
        val result=mutableListOf<StockNeed>()
        fun add(id:String,variant:String,unit:String,amount:Long){
            val m=active[id] ?: return
            if(amount>0L) result+=StockNeed(id,m.name,variant,unit,amount)
        }
        val pieceCount=pieces.sumOf { it.quantity.toLong() }
        val areaMicros=pieces.sumOf { Math.multiplyExact(Math.multiplyExact(it.widthCm.toLong(),it.heightCm.toLong()),Math.multiplyExact(it.quantity.toLong(),100L)) }
        val perimeterMicros=pieces.sumOf { Math.multiplyExact((2L*(it.widthCm+it.heightCm)+20L),Math.multiplyExact(it.quantity.toLong(),10_000L)) }
        selectedIds.filter { id -> active[id]?.let { unitFor(it)!=null }==true && id!="packaging_bundle" && !id.startsWith("pack_") }.forEach { id ->
            val m=active.getValue(id)
            when(id){
                "backboard_3mm" -> pieces.forEach { p -> add(id,sizeKey(p.widthCm,p.heightCm),"PIECE",p.quantity*SCALE) }
                "frame_pvc" -> add(id,colorKey(frameColor),"METER",perimeterMicros)
                "glass" -> add(id,"","SQM",areaMicros)
                else -> when(m.calculationType){
                    "PER_PIECE" -> add(id,"","PIECE",Math.multiplyExact(pieceCount,SCALE))
                    "PER_SET" -> add(id,"","PIECE",SCALE)
                    "PER_LINEAR_METER" -> add(id,"","METER",perimeterMicros)
                    "PER_SQUARE_METER" -> if(m.name.trim()!="عکس") add(id,"","SQM",areaMicros)
                }
            }
        }
        if("packaging_bundle" in selectedIds && active.containsKey("packaging_bundle")) {
            val packCount=((pieceCount+2)/3).coerceAtLeast(1L)
            val largest=pieces.maxBy { it.widthCm.toLong()*it.heightCm.toLong() }
            val size=packagingSizeKey.takeIf { it.matches(Regex("[0-9]+x[0-9]+")) } ?: sizeKey(largest.widthCm,largest.heightCm)
            add("pack_foam",size,"PIECE",packCount*SCALE)
            add("pack_carton",size,"PIECE",packCount*SCALE)
            add("pack_tape","","PIECE",packCount*SCALE)
        }
        return result.groupBy { it.stockId }.values.map { group -> group.first().copy(amountMicros=group.sumOf { it.amountMicros }) }
    }
}
