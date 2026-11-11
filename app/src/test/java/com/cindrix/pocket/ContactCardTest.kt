package com.cindrix.pocket

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactCardTest {
    @Test
    fun roundTripKeepsLineBreaks() {
        val c = ContactCard("Mom 555-0100\nDr. Lee 555-0199", "Meet at the school lot.\nCall Mom first.", "Sam: allergy to peanuts")
        assertEquals(c, ContactCardStore.decode(ContactCardStore.encode(c)))
    }

    @Test
    fun anEmptyOrMissingCardIsEmpty() {
        assertTrue(ContactCardStore.decode("").isEmpty)
        val dir = File.createTempFile("ccard", "").let { it.delete(); it.mkdirs(); it }
        val s = ContactCardStore(dir)
        assertTrue(s.load().isEmpty)
        assertTrue(s.save(ContactCard("A", "B", "C")))
        assertEquals("B", s.load().plan)
    }
}
