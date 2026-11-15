package com.cindrix.pocket

/** One skill from the list Cindrix Ops carries (`skills.csv`). */
data class SkillDef(val skill: String, val area: String, val proof: String, val mustHave: Boolean) {
    /** The Academy course that proves this skill, or null when it is proved by a drill or by hand. */
    val course: String? get() = proof.removePrefix("course:").takeIf { proof.startsWith("course:") }
}

/** How one skill stands for the person holding this phone. */
enum class SkillState { CAN_DO, LEARNING, NOT_YET }

/** The skills list and what it says about this person. Read-only list; the ticks stay on the phone. */
object Skills {
    /** Reads `skill,area,proof,must_have`. Returns null when the header is not that. */
    fun parse(text: String): List<SkillDef>? {
        val lines = text.lines().map { it.trimEnd('\r') }.filter { it.isNotBlank() && !it.startsWith("#") }
        if (lines.isEmpty()) return null
        val header = StockList.splitLine(lines[0])
        val cSkill = header.indexOf("skill")
        val cArea = header.indexOf("area")
        val cProof = header.indexOf("proof")
        val cMust = header.indexOf("must_have")
        if (cSkill < 0 || cArea < 0 || cProof < 0) return null
        val out = ArrayList<SkillDef>()
        for (line in lines.drop(1)) {
            val f = StockList.splitLine(line)
            val name = f.getOrNull(cSkill)?.trim().orEmpty()
            if (name.isEmpty()) continue
            val must = f.getOrNull(cMust)?.trim()?.lowercase() ?: "yes"
            out.add(SkillDef(name, f.getOrNull(cArea)?.trim().orEmpty(), f.getOrNull(cProof)?.trim().orEmpty(), must != "no"))
        }
        return out
    }

    /**
     * A skill is CAN_DO when the person ticked it, or when its course is finished (every lesson done).
     * A course that is started but not finished reads as LEARNING.
     */
    fun state(def: SkillDef, ticked: Set<String>, lessonsDone: (String) -> Pair<Int, Int>): SkillState {
        if (def.skill in ticked) return SkillState.CAN_DO
        val c = def.course ?: return SkillState.NOT_YET
        val (done, total) = lessonsDone(c)
        return when {
            total > 0 && done >= total -> SkillState.CAN_DO
            done > 0 -> SkillState.LEARNING
            else -> SkillState.NOT_YET
        }
    }

    /** (must-have skills this person can do, must-have skills in all). */
    fun covered(defs: List<SkillDef>, ticked: Set<String>, lessonsDone: (String) -> Pair<Int, Int>): Pair<Int, Int> {
        val must = defs.filter { it.mustHave }
        return must.count { state(it, ticked, lessonsDone) == SkillState.CAN_DO } to must.size
    }
}

/** One wild food and the months it can be found (`forage-seasons.csv`). */
data class Forage(val item: String, val region: String, val fromMonth: Int, val toMonth: Int)

object InSeason {
    fun parse(text: String): List<Forage>? {
        val lines = text.lines().map { it.trimEnd('\r') }.filter { it.isNotBlank() && !it.startsWith("#") }
        if (lines.isEmpty()) return null
        val header = StockList.splitLine(lines[0])
        val cItem = header.indexOf("item")
        val cRegion = header.indexOf("region")
        val cFrom = header.indexOf("from_month")
        val cTo = header.indexOf("to_month")
        if (cItem < 0 || cRegion < 0 || cFrom < 0 || cTo < 0) return null
        val out = ArrayList<Forage>()
        for (line in lines.drop(1)) {
            val f = StockList.splitLine(line)
            val from = f.getOrNull(cFrom)?.trim()?.toIntOrNull() ?: continue
            val to = f.getOrNull(cTo)?.trim()?.toIntOrNull() ?: continue
            if (from !in 1..12 || to !in 1..12) continue
            out.add(Forage(f.getOrNull(cItem)?.trim() ?: continue, f.getOrNull(cRegion)?.trim()?.lowercase() ?: "both", from, to))
        }
        return out
    }

    /** What is in season in [month] (1 to 12). A range may wrap the New Year (10 to 1). [region] "wv" or "tn" keeps that state and the shared items; empty keeps all. */
    fun now(list: List<Forage>, month: Int, region: String = ""): List<Forage> {
        val r = region.trim().lowercase()
        return list.filter {
            val inRange = if (it.fromMonth <= it.toMonth) month in it.fromMonth..it.toMonth else month >= it.fromMonth || month <= it.toMonth
            inRange && (r.isEmpty() || it.region == "both" || it.region == r)
        }
    }
}
