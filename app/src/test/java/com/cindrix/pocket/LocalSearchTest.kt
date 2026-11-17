package com.cindrix.pocket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSearchTest {
    private fun card(id: String, title: String, text: String) = Card(id, title, "medical", listOf(Block(BlockKind.PARAGRAPH, text)), false)

    @Test
    fun littleWordsAreDroppedAndPluralsTrimmed() {
        assertEquals(listOf("treat", "burn"), LocalSearch.words("How do I treat burns?"))
        assertTrue(LocalSearch.words("what is it").isEmpty())
    }

    @Test
    fun aTitleMatchOutranksABodyMatchAndNothingMatchedGivesNothing() {
        val cards = listOf(
            card("a", "Water purification", "Boil water for one minute. Burns can happen near the fire."),
            card("b", "Burns", "Cool the burn under running water for twenty minutes."),
        )
        val hits = LocalSearch.search("how do I treat a burn", cards, emptyList(), emptyList())
        assertEquals("Burns", hits.first().title)
        assertEquals(2, hits.size)
        assertTrue(LocalSearch.search("zebra migration", cards, emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun lessonsPlansAndBooksAreSearchedToo() {
        val lesson = Lesson("c/m/l", "Finding water", 5, "", listOf(Block(BlockKind.PARAGRAPH, "Look for water in low ground.")), emptyList())
        val course = Course("c", "Basics", "", "", 3, listOf(Module("m", "M", "", listOf(lesson))))
        val plan = Plan("p", "Power cut", "What to do when the power fails", "scenario", listOf(PlanStep("Light", listOf("Find the lantern"), "", null, "")))
        val hits = LocalSearch.search("lantern power", emptyList(), listOf(course), listOf(plan), listOf("Field guide" to "The lantern burns oil."))
        assertEquals(setOf("Plan", "Book"), hits.map { it.kind }.toSet())
        assertEquals("Lesson", LocalSearch.search("water low ground", emptyList(), listOf(course), emptyList()).first().kind)
    }

    @Test
    fun theSnippetCentresOnTheMatchAndIsShort() {
        val long = "x ".repeat(100) + "tourniquet goes high and tight " + "y ".repeat(100)
        val s = LocalSearch.snippet(long, listOf("tourniquet"))
        assertTrue(s.contains("tourniquet"))
        assertTrue(s.length < 180)
    }
}
