package com.tablodecori.app.pricing

import java.math.BigDecimal
import java.math.RoundingMode

class PricingEngine {
    private val hundred = BigDecimal("100")
    private val tenThousand = BigDecimal("10000")

    fun calculate(
        pieces: List<PieceInput>,
        materials: List<MaterialInput>,
        enabledMaterialIds: Set<String> = materials.filter { it.enabled }.map { it.id }.toSet(),
        profitByPieceCount: Map<Int, Long>,
        roundingStepToman: Long = 10_000L,
        customFormulas: Map<String,String> = emptyMap(),
        manualProfitToman: Long? = null,
        profitFormula: String = "",
    ): PricingResult {
        require(pieces.isNotEmpty()) { "حداقل یک سایز لازم است." }
        require(pieces.all { it.widthCm > 0 && it.heightCm > 0 && it.quantity > 0 }) { "ابعاد و تعداد باید بزرگ‌تر از صفر باشند." }
        require(materials.all { it.priceToman >= 0 && it.rateBasisPoints >= 0 && it.wasteBasisPoints >= 0 }) { "قیمت و درصد نمی‌تواند منفی باشد." }

        val count = pieces.sumOf { it.quantity }
        val area = pieces.fold(BigDecimal.ZERO) { sum, p ->
            sum + BigDecimal(p.widthCm).multiply(BigDecimal(p.heightCm)).multiply(BigDecimal(p.quantity)).divide(tenThousand)
        }
        val perimeter = pieces.fold(BigDecimal.ZERO) { sum, p ->
            val one = BigDecimal(2).multiply(BigDecimal(p.widthCm + p.heightCm)).add(BigDecimal(20)).divide(hundred)
            sum + one.multiply(BigDecimal(p.quantity))
        }
        val maxArea = pieces.maxOf { BigDecimal(it.widthCm).multiply(BigDecimal(it.heightCm)).divide(tenThousand) }

        val active = materials.filter { it.enabled && it.id in enabledMaterialIds }
        val photoMaterials = active.filter { it.id.startsWith("photo_") }
        val regularMaterials = active.filterNot { it.id.startsWith("photo_") || (it.name.trim() == "عکس" && it.calculationType == CalculationType.PER_SQUARE_METER) }
        val lines = mutableListOf<CostLine>()
        var subtotalExact = BigDecimal.ZERO
        var production = 0L
        var packaging = 0L
        var overhead = 0L

        regularMaterials.filter { it.calculationType != CalculationType.PERCENT_OF_COST }.forEach { m ->
            val wasteFactor = BigDecimal(10_000 + m.wasteBasisPoints).divide(tenThousand)
            val basePrice = BigDecimal(m.priceToman)
            val custom = customFormulas[m.id].orEmpty()
            val raw = if(custom.isNotBlank()) pieces.fold(BigDecimal.ZERO){acc,p -> acc + FormulaEvaluator.evaluate(custom,mapOf("width" to BigDecimal(p.widthCm),"height" to BigDecimal(p.heightCm),"qty" to BigDecimal(p.quantity),"unitPrice" to basePrice,"area" to BigDecimal(p.widthCm*p.heightCm).divide(tenThousand),"perimeter" to BigDecimal(2*(p.widthCm+p.heightCm)+20).divide(hundred),"count" to BigDecimal(count),"subtotal" to subtotalExact))} else when (m.calculationType) {
                CalculationType.PER_SQUARE_METER -> area.multiply(basePrice)
                CalculationType.PER_LINEAR_METER -> perimeter.multiply(basePrice)
                CalculationType.PER_PIECE -> BigDecimal(count).multiply(basePrice)
                CalculationType.PER_SET -> basePrice
                CalculationType.SMART_PACKAGING -> smartPackagingCost(m.smartKind, basePrice, count, maxArea, area)
                CalculationType.PERCENT_OF_COST -> BigDecimal.ZERO
            }
            val exact = raw.multiply(wasteFactor)
            val rounded = exact.setScale(0, RoundingMode.HALF_UP).longValueExact()
            subtotalExact += exact
            lines += CostLine(m.id, m.name, m.category, rounded)
            when (m.category) {
                MaterialCategory.PRODUCTION -> production += rounded
                MaterialCategory.PACKAGING -> packaging += rounded
                MaterialCategory.OVERHEAD -> overhead += rounded
            }
        }

        photoMaterials.forEach { m ->
            val size = m.id.removePrefix("photo_").split("x").mapNotNull { it.toIntOrNull() }
            if (size.size == 2) {
                val qty = pieces.filter { (it.widthCm == size[0] && it.heightCm == size[1]) || (it.widthCm == size[1] && it.heightCm == size[0]) }.sumOf { it.quantity }
                if (qty > 0) {
                    val rounded = BigDecimal(m.priceToman).multiply(BigDecimal(qty)).setScale(0, RoundingMode.HALF_UP).longValueExact()
                    subtotalExact += BigDecimal(rounded); production += rounded
                    lines += CostLine(m.id, "عکس ${size[0]}×${size[1]}", m.category, rounded)
                }
            }
        }

        // Percentage overheads are intentionally sequential, matching the supplied prototype.
        regularMaterials.filter { it.calculationType == CalculationType.PERCENT_OF_COST }.forEach { m ->
            val exact = subtotalExact.multiply(BigDecimal(m.rateBasisPoints)).divide(tenThousand, 8, RoundingMode.HALF_UP)
            val rounded = exact.setScale(0, RoundingMode.HALF_UP).longValueExact()
            subtotalExact += exact
            lines += CostLine(m.id, m.name, m.category, rounded)
            overhead += rounded
        }

        val cost = subtotalExact.setScale(0, RoundingMode.HALF_UP).longValueExact()
        val profit = when {
            profitFormula.isNotBlank() -> FormulaEvaluator.evaluate(profitFormula,mapOf("count" to BigDecimal(count),"area" to area,"perimeter" to perimeter,"cost" to BigDecimal(cost))).setScale(0,RoundingMode.HALF_UP).longValueExact()
            manualProfitToman != null -> manualProfitToman
            else -> 0L
        }
        require(profit >= 0) { "سود نمی‌تواند منفی باشد." }
        val rawSale = Math.addExact(cost, profit)
        val finalSale = roundUp(rawSale, roundingStepToman.coerceAtLeast(1L))
        return PricingResult(count, area, perimeter, production, packaging, overhead, cost, profit, finalSale, lines)
    }

    private fun profitForCount(count: Int, rules: Map<Int, Long>): Long {
        if (rules.isEmpty()) return 0L
        return rules[count] ?: rules[rules.keys.filter { it <= count }.maxOrNull()] ?: rules[rules.keys.maxOrNull()] ?: 0L
    }

    private fun smartPackagingCost(
        kind: SmartPackagingKind,
        base: BigDecimal,
        count: Int,
        maxArea: BigDecimal,
        totalArea: BigDecimal,
    ): BigDecimal {
        val packs = ((count + 2) / 3).coerceAtLeast(1)
        val size = when {
            maxArea > BigDecimal("0.70") -> BigDecimal("2.70")
            maxArea > BigDecimal("0.54") -> BigDecimal("2.35")
            maxArea > BigDecimal("0.35") -> BigDecimal("1.90")
            maxArea > BigDecimal("0.24") -> BigDecimal("1.45")
            else -> BigDecimal.ONE
        }
        return when (kind) {
            SmartPackagingKind.FOAM -> {
                val countFactor = BigDecimal(count).divide(BigDecimal(3), 8, RoundingMode.HALF_UP).max(BigDecimal.ONE)
                // A mild area factor makes the rule responsive to total area while preserving the prototype's behavior for normal sets.
                val areaFactor = totalArea.divide(BigDecimal(count.coerceAtLeast(1)), 8, RoundingMode.HALF_UP)
                    .divide(BigDecimal("0.45"), 8, RoundingMode.HALF_UP).max(BigDecimal.ONE)
                base.multiply(size).multiply(countFactor).multiply(areaFactor)
            }
            SmartPackagingKind.CARTON -> base.multiply(BigDecimal("0.85") + BigDecimal("0.15").multiply(size)).multiply(BigDecimal(packs))
            SmartPackagingKind.TAPE, SmartPackagingKind.STRAP -> base.multiply(BigDecimal(packs))
            SmartPackagingKind.GENERIC -> base.multiply(BigDecimal(packs))
        }
    }

    private fun roundUp(value: Long, step: Long): Long = if (value % step == 0L) value else Math.multiplyExact((value / step) + 1L, step)
}
