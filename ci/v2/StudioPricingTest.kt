package com.tablodecori.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.json.JSONObject

class StudioPricingTest {
    @Test fun sizeSearchAcceptsStoreAndKeyboardSpellings() {
        for (size in listOf("۳۰ × ۴۰", "30x40", "٣٠ در ٤٠", "30*40")) {
            assertEquals("30×40", studioSearchText(size))
        }
        assertEquals("تک تکه", studioSearchText("تك‌تكه"))
    }

    @Test fun priceCardsKeepServerOrderAndSeparateIdenticallyNamedSizes() {
        fun row(key: String, item: Int) = StudioRow(key, "revision", "دسته", "مدیوم", "100", 1, 1, 0, 0,
            true, JSONObject("""{"size_key":"medium","group_id":"","product_ids":[$item]}"""), "", "", "", emptyList())
        val catalog = JSONObject("""{"cards":[
            {"key":"first","kind":"suggested","name":"اول","products":1,"rows":["a"],"images":[]},
            {"key":"second","kind":"suggested","name":"دوم","products":1,"rows":["b"],"images":[]},
            {"key":"ambiguous","kind":"review","name":"بررسی","products":1,"rows":[],"images":[]}
        ]}""")
        val cards = studioCards(catalog, listOf(row("b", 2), row("a", 1)))
        assertEquals(listOf("first", "second", "ambiguous"), cards.map { it.key })
        assertEquals(listOf(listOf("a"), listOf("b"), emptyList()), cards.map { card -> card.rows.map { it.key } })
        assertEquals(2, studioCards(null, listOf(row("a", 1), row("b", 2))).size)
    }

    @Test fun malformedCatalogEntriesDoNotPreventRenderingValidCards() {
        val row = StudioRow("row", "rev", "دسته", "مدیوم", "100", 1, 1, 0, 0, true,
            JSONObject("""{"size_key":"medium","group_id":"","product_ids":[1]}"""), "", "", "", emptyList())
        val catalog = JSONObject("""{"cards":[null,{"key":"group","kind":"suggested","rows":["row","row"]},{"key":"group","rows":[]}]}""")
        assertEquals(listOf("group"), studioCards(catalog, listOf(row)).map { it.key })
        assertEquals(listOf("row"), studioCards(catalog, listOf(row)).single().rows.map { it.key })
        val broken = StudioState(catalog = JSONObject("""{"references":[null,{"key":"incomplete"}],"rows":[{}]}"""))
        assertEquals(emptyList<StudioRow>(), broken.rows)
    }

    @Test fun manualProfitCanMatchOnlyARepresentableStorePrice() {
        assertEquals(170000L, studioProfitFromStorePrice(400000, 230000, 10000))
        assertNull(studioProfitFromStorePrice(230001, 230000, 10000))
        assertNull(studioProfitFromStorePrice(200000, 230000, 10000))
        assertNull(studioProfitFromStorePrice(null, 230000, 10000))
    }

    @Test fun pricesKeepExactStoreUnits() {
        assertEquals("1600000", studioPrice("۱٬۶۰۰٬۰۰۰", 0))
        assertEquals("1600.05", studioPrice("١٦٠٠٫٠٥", 2))
        assertEquals("200", studioPrice("200.000", 0))
        assertEquals("999999999999", studioPrice("999999999999", 0))
    }

    @Test fun malformedOrUnsupportedPricesAreRejected() {
        listOf("0", "-100", "1e6", "۱۰۰ تومان", "1000000000000", "12.001").forEach {
            assertNull(it, studioPrice(it, 2))
        }
    }
}
