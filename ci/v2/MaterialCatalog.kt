package com.tablodecori.app.data

import com.tablodecori.app.data.db.MaterialEntity

/** IDs from the first app generation coexist in upgraded databases. */
object MaterialCatalog {
    const val STOCK_ONLY = "STOCK_ONLY"
    const val RELIEF_CUSTOM = "RELIEF_CUSTOM"
    private val aliases=mapOf(
        "frame" to "frame_pvc", "back" to "backboard_3mm", "photo" to "photo_lab",
        "frameSup" to "frame_supplies", "labor" to "production_labor",
        "foam" to "packaging_bundle", "carton" to "packaging_bundle",
        "tape" to "packaging_bundle", "label" to "packaging_bundle",
        "strap" to "packaging_bundle", "packWorker" to "packaging_bundle",
        "unexpected" to "unexpected_cost",
    )
    fun canonicalId(id:String)=aliases[id]?:id
    fun isLegacy(id:String)=id in aliases
    fun mainMaterials(items:List<MaterialEntity>)=items.filter { !it.deleted && !isLegacy(it.id) && !it.id.startsWith("pack_") && it.smartKind!=STOCK_ONLY }
    fun selectableForProduct(items:List<MaterialEntity>)=mainMaterials(items)
    fun selectableForQuick(items:List<MaterialEntity>)=selectableForProduct(items).filter { it.id!="packaging_bundle" }
    fun selectableForRelief(material:MaterialEntity)=material.id in setOf("frame_pvc","packaging_bundle") || (material.smartKind==RELIEF_CUSTOM && material.category=="PRODUCTION")
    fun selectedCanonicalIds(savedIds:Set<String>)=savedIds.map(::canonicalId).toSet()
}
