package com.tablodecori.app.data

import com.tablodecori.app.data.db.MaterialEntity

/** IDs from the first app generation coexist in upgraded databases. */
object MaterialCatalog {
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
    fun mainMaterials(items:List<MaterialEntity>)=items.filter { !it.deleted && !isLegacy(it.id) && !it.id.startsWith("pack_") }
    fun selectableForProduct(items:List<MaterialEntity>)=mainMaterials(items)
    fun selectedCanonicalIds(savedIds:Set<String>)=savedIds.map(::canonicalId).toSet()
}
