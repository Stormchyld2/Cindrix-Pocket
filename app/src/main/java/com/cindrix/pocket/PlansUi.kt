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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The plans carried from Cindrix Ops: read the steps, tick them off. Works with no Hub and no network. */
@Composable
fun PlansScreen(plans: List<Plan>, progress: LearnProgress, store: Store, onBack: () -> Unit) {
    var open by remember { mutableStateOf<Plan?>(null) }
    BackHandler { if (open != null) open = null else onBack() }
    val done = remember { mutableStateMapOf<String, Long>().also { it.putAll(Plans.decode(store.planTicks)) } }
    val today = System.currentTimeMillis() / 86_400_000L
    val lessonDone: (String) -> Boolean = { progress.done(it) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (open != null) open = null else onBack() }) { Text("BACK", fontFamily = HeadingFont) }
            Text(open?.title ?: "Plans", fontFamily = HeadingFont, fontSize = 18.sp, modifier = Modifier.weight(1f).padding(start = 4.dp))
        }
        val plan = open
        if (plan == null) {
            val groups = plans.groupBy { it.kind }.toList().sortedBy { (k, _) -> listOf("scenario", "prevent", "checks", "learning", "planning").indexOf(k).let { if (it < 0) 99 else it } }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((kind, list) in groups) {
                    item { Text(Plans.kindLabel(kind).uppercase(), fontFamily = HeadingFont, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
                    items(list, key = { it.id }) { p ->
                        val (n, total) = Plans.progress(p, done, today, lessonDone)
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).clickable { open = p }.padding(12.dp)) {
                            Text(p.title, fontSize = 16.sp)
                            if (p.summary.isNotBlank()) Text(p.summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$n of $total steps done", fontSize = 12.sp, fontFamily = HeadingFont)
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (plan.summary.isNotBlank()) item { Text(plan.summary, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                itemsIndexed(plan.steps) { idx, step ->
                    val key = Plans.key(plan, idx)
                    val isDone = Plans.stepDone(step, key, done, today, lessonDone)
                    val auto = step.check.startsWith("lesson:") && lessonDone(step.check.removePrefix("lesson:"))
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface)
                            .clickable(enabled = !auto) {
                                if (isDone) done.remove(key) else done[key] = today
                                store.planTicks = Plans.encode(done)
                            }.padding(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isDone) "[x]" else "[ ]", fontFamily = HeadingFont, modifier = Modifier.padding(end = 10.dp))
                            Text(step.title, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        }
                        for (line in step.text) if (line.isNotBlank()) Text(line, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
                        val notes = ArrayList<String>()
                        step.every?.let { notes.add("Comes back every $it days") }
                        if (auto) notes.add("Ticked by finishing the lesson")
                        else if (step.check.startsWith("lesson:")) notes.add("Finish the lesson in the Academy to tick this")
                        if (step.button.isNotBlank()) notes.add("In the Ops program: ${step.button}")
                        if (notes.isNotEmpty()) Text(notes.joinToString(". "), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
