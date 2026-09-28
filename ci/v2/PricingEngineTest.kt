package com.tablodecori.app.pricing

import org.junit.Assert.*
import org.junit.Test

class PricingEngineTest {
    private val engine=PricingEngine()
    private val profits=mapOf(1 to 250000L,2 to 350000L,3 to 450000L,4 to 650000L,5 to 800000L,6 to 950000L,7 to 1100000L,8 to 1250000L,9 to 1400000L)
    private fun m(id:String,type:CalculationType,price:Long=0,waste:Int=0,rate:Int=0,cat:MaterialCategory=MaterialCategory.PRODUCTION)=MaterialInput(id,id,cat,type,price,rate,waste,true)

    @Test fun glass20x30(){val r=engine.calculate(listOf(PieceInput(20,30,1)),listOf(m("glass",CalculationType.PER_SQUARE_METER,300000)),setOf("glass"),profits,1);assertEquals(18000,r.costBeforeProfitToman);assertEquals("0.06",r.totalAreaSquareMeters.stripTrailingZeros().toPlainString())}
    @Test fun glassWaste5Percent(){val r=engine.calculate(listOf(PieceInput(20,30,1)),listOf(m("glass",CalculationType.PER_SQUARE_METER,300000,500)),setOf("glass"),profits,1);assertEquals(18900,r.costBeforeProfitToman)}
    @Test fun pvcPerimeterIncludesTwentyCentimetersPerPiece(){val r=engine.calculate(listOf(PieceInput(20,30,1)),listOf(m("pvc",CalculationType.PER_LINEAR_METER,185000)),setOf("pvc"),profits,1);assertEquals("1.2",r.totalPerimeterMeters.stripTrailingZeros().toPlainString());assertEquals(222000,r.costBeforeProfitToman)}
    @Test fun fourPieceIrregularSet(){val pieces=listOf(PieceInput(40,60,1),PieceInput(20,30,2),PieceInput(16,21,1));val r=engine.calculate(pieces,listOf(m("glass",CalculationType.PER_SQUARE_METER,300000,500)),setOf("glass"),profits,1);assertEquals(4,r.pieceCount);assertEquals("0.3936",r.totalAreaSquareMeters.toPlainString());assertEquals(123984,r.costBeforeProfitToman);assertEquals(0,r.profitToman)}
    @Test fun ninePieceSet(){val p=listOf(PieceInput(40,60,1),PieceInput(30,40,4),PieceInput(20,30,4));val r=engine.calculate(p,listOf(m("labor",CalculationType.PER_PIECE,65000)),setOf("labor"),profits,1);assertEquals(9,r.pieceCount);assertEquals(585000,r.costBeforeProfitToman);assertEquals(0,r.profitToman)}
    @Test fun glassCanBeDisabled(){val p=listOf(PieceInput(40,60,1));val mats=listOf(m("glass",CalculationType.PER_SQUARE_METER,300000),m("labor",CalculationType.PER_PIECE,65000));val on=engine.calculate(p,mats,setOf("glass","labor"),profits,1);val off=engine.calculate(p,mats,setOf("labor"),profits,1);assertTrue(on.costBeforeProfitToman>off.costBeforeProfitToman);assertFalse(off.lines.any{it.materialId=="glass"})}
    @Test fun backboardCanBeDisabled(){val p=listOf(PieceInput(40,60,1));val mats=listOf(m("back",CalculationType.PER_SQUARE_METER,145000),m("labor",CalculationType.PER_PIECE,65000));val r=engine.calculate(p,mats,setOf("labor"),profits,1);assertFalse(r.lines.any{it.materialId=="back"});assertEquals(65000,r.costBeforeProfitToman)}
    @Test fun inflationAppliedBeforeProfit(){val mats=listOf(m("base",CalculationType.PER_SET,100000),m("inflation",CalculationType.PERCENT_OF_COST,rate=700,cat=MaterialCategory.OVERHEAD));val r=engine.calculate(listOf(PieceInput(20,30,1)),mats,mats.map{it.id}.toSet(),profits,1);assertEquals(107000,r.costBeforeProfitToman);assertEquals(0,r.profitToman)}
    @Test fun unexpectedCostApplied(){val mats=listOf(m("base",CalculationType.PER_SET,100000),m("unexpected",CalculationType.PERCENT_OF_COST,rate=300,cat=MaterialCategory.OVERHEAD));val r=engine.calculate(listOf(PieceInput(20,30,1)),mats,mats.map{it.id}.toSet(),profits,1);assertEquals(103000,r.costBeforeProfitToman)}
    @Test fun manualProfitIsPerProduct(){val r=engine.calculate(listOf(PieceInput(20,30,3)),emptyList(),emptySet(),profits,1,manualProfitToman=450000);assertEquals(450000,r.profitToman)}
    @Test fun reliefCostsOnlyDesignFrameAndPackaging(){
        val materials=listOf(m("frame_pvc",CalculationType.PER_LINEAR_METER,100000),m("glass",CalculationType.PER_SQUARE_METER,300000))
        val result=engine.calculate(listOf(PieceInput(20,30,1)),materials,setOf("frame_pvc"),profits,1,manualProfitToman=200000,designMaterialsCostToman=150000)
        assertEquals(270000,result.costBeforeProfitToman)
        assertEquals(200000,result.profitToman)
        assertEquals(470000,result.finalPriceToman)
        assertTrue(result.lines.any{it.materialId=="design_materials"&&it.amountToman==150000L})
        assertFalse(result.lines.any{it.materialId=="glass"})
    }
    @Test fun formulaProfitIsPerProduct(){val r=engine.calculate(listOf(PieceInput(20,30,1)),listOf(m("base",CalculationType.PER_SET,100000)),setOf("base"),profits,1,profitFormula="cost*20/100");assertEquals(20000,r.profitToman);assertEquals(120000,r.finalPriceToman)}
    @Test fun shippingNeverEntersBasePrice(){val r=engine.calculate(listOf(PieceInput(20,30,1)),listOf(m("labor",CalculationType.PER_PIECE,65000)),setOf("labor"),profits,1);assertEquals(65000,r.finalPriceToman);assertEquals(65000,r.costBeforeProfitToman)}
    @Test fun realOrderProfitIncludesShipping(){assertEquals(235000,OrderMath.actualProfit(500000,200000,65000))}
    @Test fun glassPriceChangeRecalculates(){val p=listOf(PieceInput(40,60,1));val old=engine.calculate(p,listOf(m("glass",CalculationType.PER_SQUARE_METER,300000)),setOf("glass"),profits,1);val newer=engine.calculate(p,listOf(m("glass",CalculationType.PER_SQUARE_METER,350000)),setOf("glass"),profits,1);assertEquals(72000,old.costBeforeProfitToman);assertEquals(84000,newer.costBeforeProfitToman)}
    @Test fun oldOrderSnapshotDoesNotChangeAfterMaterialPriceChange(){val p=listOf(PieceInput(40,60,1));val old=engine.calculate(p,listOf(m("glass",CalculationType.PER_SQUARE_METER,300000)),setOf("glass"),profits,1);val snapshot=old.costBeforeProfitToman;engine.calculate(p,listOf(m("glass",CalculationType.PER_SQUARE_METER,350000)),setOf("glass"),profits,1);assertEquals(72000,snapshot);assertEquals(428000,OrderMath.actualProfit(500000,snapshot,0))}
}
