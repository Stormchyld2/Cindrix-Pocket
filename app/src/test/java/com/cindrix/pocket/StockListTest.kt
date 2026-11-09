package com.cindrix.pocket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StockListTest {
    private val sample = "#cindrix-stock-list v1\n" +
        "name,barcode,unit,location,qty,use_by,category,updated\n" +
        "\"Beans, black\",0123,can,Pantry,7,2028-03,Food,2026-11-01T18:00:00Z\n" +
        "\"Beans, black\",0123,can,Garage,2,2027-01,Food,2026-11-01T18:00:00Z\n" +
        "Rice,55,bag,Pantry,2.5,,Food,2026-11-01T18:00:00Z\n"

    @Test
    fun readsRowsWithQuotedCommas() {
        val lines = StockList.parse(sample)!!
        assertEquals(3, lines.size)
        assertEquals("Beans, black", lines[0].name)
        assertEquals(2.5, lines[2].qty, 0.0)
    }

    @Test
    fun otherFilesAreRefused() {
        assertNull(StockList.parse("scan_id,item\n1,Rice\n"))
    }

    @Test
    fun onHandAddsLinesAndFindsSoonestDate() {
        val r = StockList.onHand(StockList.parse(sample)!!, "0123")!!
        assertEquals(9.0, r.first, 0.0)
        assertEquals("2027-01", r.second)
        assertNull(StockList.onHand(StockList.parse(sample)!!, "999"))
    }

    @Test
    fun searchLooksAtNamePlaceAndCategory() {
        val l = StockList.parse(sample)!!
        assertEquals(1, StockList.search(l, "garage").size)
        assertEquals(1, StockList.search(l, "rice").size)
        assertEquals(3, StockList.search(l, "").size)
    }
}
