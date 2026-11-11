package com.cindrix.pocket

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {
    private val lesson = "---\ntitle: One\nminutes: 7\nstatus: complete\nsource: FEMA\n---\n" +
        "Intro line one\nline two.\n\n## Steps\n1. First\n2. Second\n\n- a\n- b\n\n> Careful **now**\n\n" +
        "## Check yourself\n? Q1\n- no\n+ yes\n- nope\n> because\n\n? Q2\n+ yes\n- no\n- x\n\n? Q3\n- no\n- x\n+ yes\n"

    @Test
    fun readsHeaderAndBlocks() {
        val (h, body) = Md.front(lesson)
        assertEquals("One", h["title"])
        assertEquals("7", h["minutes"])
        val b = Md.blocks(body.substringBefore("## Check yourself"))
        assertEquals(BlockKind.PARAGRAPH, b[0].kind)
        assertEquals("Intro line one line two.", b[0].text)
        assertEquals(BlockKind.HEADING, b[1].kind)
        assertEquals(listOf("First", "Second"), b[2].items)
        assertEquals(BlockKind.STEPS, b[2].kind)
        assertEquals(BlockKind.BULLETS, b[3].kind)
        assertEquals(BlockKind.NOTE, b[4].kind)
    }

    @Test
    fun quizNeedsThreeQuestionsAndOneRightAnswer() {
        val q = Md.quiz(lesson.substringAfter("## Check yourself"))
        assertEquals(3, q.size)
        assertEquals(1, q[0].correct)
        assertEquals("because", q[0].why)
        assertEquals(0, q[1].correct)
        assertTrue(Md.quiz("? Only\n+ a\n- b\n").isEmpty())
    }

    @Test
    fun boldIsSplitOut() {
        assertEquals(listOf("Careful " to false, "now" to true), Md.bold("Careful **now**"))
    }

    @Test
    fun theCarriedFilesAllLoad() {
        val root = File("src/main/assets")
        if (!root.isDirectory) return
        val lib = Library(FolderSource(root))
        val cards = lib.cards()
        assertTrue("cards: " + cards.size, cards.size > 100)
        assertTrue(cards.all { it.blocks.isNotEmpty() })
        val courses = lib.courses()
        assertTrue("courses: " + courses.size, courses.size >= 25)
        assertTrue(courses.any { it.tier == 1 } && courses.any { it.tier == 2 } && courses.any { it.tier == 3 })
        assertTrue(courses.all { it.lessonCount > 0 })
        assertTrue(courses.any { it.id == "survival-basics" })
        assertEquals(courses.map { it.id }.toSet().size, courses.size)
        println("pocket library: " + cards.size + " cards, " + courses.size + " courses, " + courses.sumOf { it.lessonCount } + " lessons")
    }
}
