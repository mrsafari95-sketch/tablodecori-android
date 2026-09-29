package com.tablodecori.app.data

import com.tablodecori.app.data.db.MaterialEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class MaterialCatalogTest {
    private fun material(id: String, kind: String = "GENERIC", deleted: Boolean = false) =
        MaterialEntity(
            id = id,
            name = id,
            category = "PRODUCTION",
            calculationType = "PER_SQUARE_METER",
            priceToman = 100L,
            smartKind = kind,
            deleted = deleted,
            createdAt = 1L,
            updatedAt = 1L,
        )

    @Test fun quickShowsNewPricingMaterialsWithoutStockOnlyOrInternalMaterials() {
        val materials = listOf(
            material("glass"),
            material("custom_board"),
            material("relief_detail", MaterialCatalog.RELIEF_CUSTOM),
            material("packaging_bundle"),
            material("pack_foam"),
            material("stock_only", MaterialCatalog.STOCK_ONLY),
            material("deleted_material", deleted = true),
            material("back"),
        )

        assertEquals(
            listOf("glass", "custom_board", "relief_detail"),
            MaterialCatalog.selectableForQuick(materials).map { it.id },
        )
    }
}
