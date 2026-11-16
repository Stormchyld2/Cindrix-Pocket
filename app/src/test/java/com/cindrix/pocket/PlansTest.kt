package com.cindrix.pocket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlansTest {
    private val text = """---
title: Test plan
summary: One line.
kind: scenario
version: 2
---
## First step
go: Cards
button: Open the Cards
Plain line.
- a bullet

## Comes back
every: 7
Do it weekly.

## Lesson step
check: lesson:course/mod/les
Learn it.
"""

    @Test
    fun readsTheStepsAndTheirParts() {
        val p = Plans.parse("test", text)!!
        assertEquals("Test plan", p.title)
        assertEquals("scenario", p.kind)
        assertEquals(3, p.steps.size)
        assertEquals("Open the Cards", p.steps[0].button)
        assertEquals(listOf("Plain line.", "- a bullet"), p.steps[0].text)
        assertEquals(7, p.steps[1].every)
        assertEquals("lesson:course/mod/les", p.steps[2].check)
    }

    @Test
    fun filesWithoutATitleOrStepsAreRefused() {
        assertNull(Plans.parse("x", "---\nsummary: no title\n---\n## A\ntext"))
        assertNull(Plans.parse("x", "---\ntitle: T\n---\nno steps here"))
    }

    @Test
    fun aTickHoldsAndARepeatingStepComesBack() {
        val p = Plans.parse("test", text)!!
        val none: (String) -> Boolean = { false }
        val done = mapOf("test/0" to 100L, "test/1" to 100L)
        assertTrue(Plans.stepDone(p.steps[0], "test/0", done, 5000, none))
        assertTrue(Plans.stepDone(p.steps[1], "test/1", done, 106, none))
        assertFalse(Plans.stepDone(p.steps[1], "test/1", done, 107, none))
        assertFalse(Plans.stepDone(p.steps[2], "test/2", done, 100, none))
    }

    @Test
    fun aFinishedLessonTicksItsStep() {
        val p = Plans.parse("test", text)!!
        assertTrue(Plans.stepDone(p.steps[2], "test/2", emptyMap(), 1, { it == "course/mod/les" }))
        assertEquals(1 to 3, Plans.progress(p, emptyMap(), 1, { it == "course/mod/les" }))
    }

    @Test
    fun ticksSurviveSavingAndLoading() {
        val m = mapOf("a/0" to 12L, "b/3" to 99L)
        assertEquals(m, Plans.decode(Plans.encode(m)))
        assertTrue(Plans.decode(setOf("junk", "x|notanumber")).isEmpty())
    }
}
