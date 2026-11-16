package com.cindrix.pocket

/** One step of a plan carried from Cindrix Ops (`content/plans/*.md`). */
data class PlanStep(
    val title: String,
    val text: List<String>,
    /** "Open Plans" and so on: what the button does in the Ops program. Shown as a note here. */
    val button: String,
    val every: Int?,
    /** "lesson:course/module/lesson" ticks itself when that lesson is done; other checks need the Hub. */
    val check: String,
)

data class Plan(val id: String, val title: String, val summary: String, val kind: String, val steps: List<PlanStep>)

/** Reads the plan files and works out what is done. The ticks stay on the phone. */
object Plans {
    /** The plan in [text], or null when it has no title or no steps. [id] is the file name without `.md`. */
    fun parse(id: String, text: String): Plan? {
        val lines = text.lines().map { it.trimEnd('\r') }
        val meta = HashMap<String, String>()
        var i = 0
        if (lines.firstOrNull()?.trim() == "---") {
            i = 1
            while (i < lines.size && lines[i].trim() != "---") {
                val l = lines[i]
                val c = l.indexOf(':')
                if (c > 0) meta[l.substring(0, c).trim()] = l.substring(c + 1).trim()
                i++
            }
            i++
        }
        val title = meta["title"]?.takeIf { it.isNotBlank() } ?: return null
        val steps = ArrayList<PlanStep>()
        var stepTitle: String? = null
        var body = ArrayList<String>()
        var button = ""
        var every: Int? = null
        var check = ""
        fun close() {
            val t = stepTitle ?: return
            while (body.isNotEmpty() && body.last().isBlank()) body.removeAt(body.size - 1)
            steps.add(PlanStep(t, body.toList(), button, every, check))
        }
        while (i < lines.size) {
            val l = lines[i]
            when {
                l.startsWith("## ") -> {
                    close()
                    stepTitle = l.removePrefix("## ").trim()
                    body = ArrayList(); button = ""; every = null; check = ""
                }
                stepTitle == null -> {}
                l.startsWith("go:") -> {}
                l.startsWith("button:") -> button = l.removePrefix("button:").trim()
                l.startsWith("every:") -> every = l.removePrefix("every:").trim().toIntOrNull()
                l.startsWith("check:") -> check = l.removePrefix("check:").trim()
                else -> body.add(l)
            }
            i++
        }
        close()
        if (steps.isEmpty()) return null
        return Plan(id, title, meta["summary"].orEmpty(), meta["kind"].orEmpty(), steps)
    }

    fun key(plan: Plan, index: Int) = "${plan.id}/$index"

    /**
     * Done when the person ticked it, or its lesson is finished. A repeating step is done only for
     * [PlanStep.every] days after it was ticked, then it is due again. [done] maps a key to the day (days since 1970) it was ticked.
     */
    fun stepDone(step: PlanStep, key: String, done: Map<String, Long>, today: Long, lessonDone: (String) -> Boolean): Boolean {
        if (step.check.startsWith("lesson:") && lessonDone(step.check.removePrefix("lesson:"))) return true
        val day = done[key] ?: return false
        val every = step.every ?: return true
        return today - day < every
    }

    fun progress(plan: Plan, done: Map<String, Long>, today: Long, lessonDone: (String) -> Boolean): Pair<Int, Int> =
        plan.steps.indices.count { stepDone(plan.steps[it], key(plan, it), done, today, lessonDone) } to plan.steps.size

    fun kindLabel(kind: String) = when (kind) {
        "scenario" -> "Emergencies"
        "prevent" -> "Keep it from failing"
        "checks" -> "Regular checks"
        "learning" -> "Learning"
        "planning" -> "How much to have"
        else -> "Other"
    }

    /** Encodes ticks as "key|day" for the phone's settings. */
    fun encode(done: Map<String, Long>): Set<String> = done.map { "${it.key}|${it.value}" }.toSet()
    fun decode(saved: Set<String>): Map<String, Long> =
        saved.mapNotNull { s -> val c = s.lastIndexOf('|'); if (c <= 0) null else s.substring(c + 1).toLongOrNull()?.let { s.substring(0, c) to it } }.toMap()
}
