package com.cindrix.pocket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillsTest {
    private val csv = "skill,area,proof,must_have\n" +
        "Make water safe to drink,Water,course:water,yes\n" +
        "\"Sew, mend\",Trades,drill:46,no\n" +
        "Store water,Water,hand,yes\n"

    @Test
    fun readsTheListAndQuotedCommas() {
        val l = Skills.parse(csv)!!
        assertEquals(3, l.size)
        assertEquals("Sew, mend", l[1].skill)
        assertEquals(false, l[1].mustHave)
        assertEquals("water", l[0].course)
        assertNull(l[2].course)
    }

    @Test
    fun otherFilesAreRefused() {
        assertNull(Skills.parse("name,qty\nRice,2\n"))
    }

    @Test
    fun aTickOrAFinishedCourseMeansCanDo() {
        val l = Skills.parse(csv)!!
        val none: (String) -> Pair<Int, Int> = { 0 to 5 }
        assertEquals(SkillState.NOT_YET, Skills.state(l[0], emptySet(), none))
        assertEquals(SkillState.CAN_DO, Skills.state(l[0], setOf("Make water safe to drink"), none))
        assertEquals(SkillState.LEARNING, Skills.state(l[0], emptySet()) { 2 to 5 })
        assertEquals(SkillState.CAN_DO, Skills.state(l[0], emptySet()) { 5 to 5 })
        assertEquals(SkillState.NOT_YET, Skills.state(l[2], emptySet()) { 5 to 5 })
    }

    @Test
    fun coveredCountsOnlyMustHaveSkills() {
        val l = Skills.parse(csv)!!
        val r = Skills.covered(l, setOf("Store water", "Sew, mend")) { 0 to 5 }
        assertEquals(1 to 2, r)
    }

    private val forage = "item,region,from_month,to_month\nRamps,both,4,5\nFiddleheads,wv,4,5\nDeer,both,10,1\n"

    @Test
    fun inSeasonHandlesYearWrapAndRegion() {
        val l = InSeason.parse(forage)!!
        assertEquals(listOf("Ramps", "Fiddleheads"), InSeason.now(l, 4).map { it.item })
        assertEquals(listOf("Ramps"), InSeason.now(l, 5, "tn").map { it.item })
        assertEquals(listOf("Deer"), InSeason.now(l, 12).map { it.item })
        assertEquals(listOf("Deer"), InSeason.now(l, 1).map { it.item })
        assertTrue(InSeason.now(l, 7).isEmpty())
    }

    @Test
    fun badRowsAreSkippedAndWrongFilesRefused() {
        assertEquals(1, InSeason.parse("item,region,from_month,to_month\nOk,both,3,4\nBad,both,13,2\nAlso bad,both,x,2\n")!!.size)
        assertNull(InSeason.parse("a,b\n1,2\n"))
    }
}
