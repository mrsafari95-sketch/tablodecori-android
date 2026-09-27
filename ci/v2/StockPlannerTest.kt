package com.tablodecori.app.data

import com.tablodecori.app.data.db.MaterialEntity
import com.tablodecori.app.pricing.PieceInput
import org.junit.Assert.*
import org.junit.Test

class StockPlannerTest {
    private fun material(id:String,type:String="PER_PIECE",category:String="PRODUCTION") = MaterialEntity(id,id,category,type,0,createdAt=0,updatedAt=0)

    @Test fun stockUsesDimensionsAreaAndFrameColorButNeverPhoto() {
        val materials=listOf(material("backboard_3mm","PER_SQUARE_METER"),material("glass","PER_SQUARE_METER"),material("frame_pvc","PER_LINEAR_METER"),material("photo_lab","PER_SET"),material("packaging_bundle","PER_SET","PACKAGING"),material("pack_carton","PER_SET","PACKAGING"),material("pack_foam","PER_SET","PACKAGING"),material("pack_tape","PER_SET","PACKAGING"))
        val needs=StockPlanner.needs(listOf(PieceInput(50,70,2),PieceInput(40,60,1)),setOf("backboard_3mm","glass","frame_pvc","photo_lab","packaging_bundle"),materials,"طلایی","")
        assertEquals(2_000_000L,needs.single{it.stockId=="backboard_3mm|50x70"}.amountMicros)
        assertEquals(1_000_000L,needs.single{it.stockId=="backboard_3mm|40x60"}.amountMicros)
        assertEquals(940_000L,needs.single{it.materialId=="glass"}.amountMicros)
        assertEquals(7_400_000L,needs.single{it.stockId=="frame_pvc|طلایی"}.amountMicros)
        assertEquals(1_000_000L,needs.single{it.stockId=="pack_carton|50x70"}.amountMicros)
        assertFalse(needs.any{it.materialId=="photo_lab"})
    }

    @Test fun smallBackboardExceptionMatchesPricingAndVariantsNormalize() {
        val materials=listOf(material("backboard_3mm"),material("glass"))
        val needs=StockPlanner.needs(listOf(PieceInput(21,30,1),PieceInput(45,30,1)),setOf("backboard_3mm","glass"),materials,"","")
        assertEquals(1,needs.count{it.materialId=="backboard_3mm"})
        assertEquals("30x45",needs.single{it.materialId=="backboard_3mm"}.variantKey)
        assertEquals("50x70",StockPlanner.variantFor("backboard_3mm","70x50"))
    }
}
