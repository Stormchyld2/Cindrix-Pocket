package com.cindrix.pocket

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

/** The skills list for this person and what is in season now. Both work with no Hub and no network. */
@Composable
fun SkillsScreen(
    skills: List<SkillDef>,
    forage: List<Forage>,
    courses: List<Course>,
    progress: LearnProgress,
    store: Store,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val ticked = remember { mutableStateListOf<String>().also { it.addAll(store.mySkills) } }
    var region by remember { mutableStateOf(store.forageRegion) }
    val month = remember { Calendar.getInstance().get(Calendar.MONTH) + 1 }

    fun lessonsDone(courseId: String): Pair<Int, Int> {
        val c = courses.firstOrNull { it.id == courseId } ?: return 0 to 0
        val ids = c.modules.flatMap { m -> m.lessons.map { it.id } }
        return ids.count { progress.done(it) } to ids.size
    }

    val set = ticked.toSet()
    val (covered, total) = Skills.covered(skills, set, ::lessonsDone)
    val inSeason = InSeason.now(forage, month, region)

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("BACK", fontFamily = HeadingFont) }
            Text("Skills and season", fontFamily = HeadingFont, fontSize = 18.sp, modifier = Modifier.weight(1f).padding(start = 4.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
                    Text("IN SEASON NOW: " + MONTHS[month - 1].uppercase(), fontFamily = HeadingFont, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (inSeason.isEmpty()) "Nothing on the list this month. Eat from what you stored." else inSeason.joinToString(", ") { it.item },
                        fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Check your state's seasons and licences before you hunt or fish. Be certain of every plant.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((label, value) in listOf("ALL" to "", "WEST VIRGINIA" to "wv", "TENNESSEE" to "tn")) {
                            TextButton(onClick = { region = value; store.forageRegion = value }) {
                                Text(label + if (region == value) "  *" else "", fontFamily = HeadingFont, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
                    Text("MY SKILLS", fontFamily = HeadingFont, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("$covered of $total must-have skills", fontSize = 18.sp)
                    Text(
                        "Tap a skill to mark that you can really do it. A skill with a course fills in when you finish the course. This stays on the phone; the Hub's grid counts a skill only when someone signs it off there.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val byArea = skills.groupBy { it.area }
            for ((area, list) in byArea) {
                item { Text(area.uppercase(), fontFamily = HeadingFont, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
                items(list, key = { it.skill }) { def ->
                    val st = Skills.state(def, set, ::lessonsDone)
                    val mark = when (st) { SkillState.CAN_DO -> "[x]"; SkillState.LEARNING -> "[~]"; SkillState.NOT_YET -> "[ ]" }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface)
                            .clickable {
                                if (def.skill in ticked) ticked.remove(def.skill) else ticked.add(def.skill)
                                store.mySkills = ticked.toSet()
                            }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(mark, fontFamily = HeadingFont, modifier = Modifier.padding(end = 10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(def.skill, fontSize = 15.sp)
                            Text(
                                (if (def.mustHave) "Must-have" else "Extra") + (def.course?.let { " - course" } ?: if (def.proof.startsWith("drill:")) " - drill" else ""),
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
