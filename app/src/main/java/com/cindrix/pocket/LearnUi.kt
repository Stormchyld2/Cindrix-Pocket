package com.cindrix.pocket

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Best quiz score for each lesson, kept on the phone only. */
class LearnProgress(context: Context) {
    private val prefs = context.getSharedPreferences("learn-progress", Context.MODE_PRIVATE)
    fun best(lessonId: String): Int = prefs.getInt(lessonId, -1)
    fun record(lessonId: String, percent: Int) {
        if (percent > best(lessonId)) prefs.edit().putInt(lessonId, percent).apply()
    }
    /** A lesson counts as done at 70 percent, the same bar the program uses; a lesson with no quiz counts when opened. */
    fun done(lessonId: String): Boolean = best(lessonId) >= 0
}

fun rich(text: String): AnnotatedString = buildAnnotatedString {
    for ((t, b) in Md.bold(text)) {
        if (b) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(t) } else append(t)
    }
}

@Composable
private fun TopRow(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("BACK", fontFamily = HeadingFont) }
        Text(title, fontFamily = HeadingFont, fontSize = 18.sp, modifier = Modifier.weight(1f).padding(start = 4.dp), maxLines = 2)
    }
}

@Composable
private fun Tile(onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick).padding(14.dp),
    ) { content() }
}

@Composable
fun Blocks(blocks: List<Block>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (b in blocks) {
            when (b.kind) {
                BlockKind.HEADING -> Text(b.text, fontFamily = HeadingFont, fontSize = 17.sp, color = Blue, modifier = Modifier.padding(top = 8.dp))
                BlockKind.PARAGRAPH -> Text(rich(b.text), fontSize = 16.sp, lineHeight = 23.sp)
                BlockKind.BULLETS -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    b.items.forEach { Row { Text("•  ", color = Blue, fontSize = 16.sp); Text(rich(it), fontSize = 16.sp, lineHeight = 23.sp) } }
                }
                BlockKind.STEPS -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    b.items.forEachIndexed { i, s -> Row { Text("${i + 1}.  ", color = Blue, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text(rich(s), fontSize = 16.sp, lineHeight = 23.sp) } }
                }
                BlockKind.NOTE -> Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(PanelHigh).padding(12.dp)) {
                    Text(rich(b.text), fontSize = 15.sp, lineHeight = 22.sp)
                }
            }
        }
    }
}

// ---------- cards ----------

@Composable
fun CardsScreen(cards: List<Card>, onBack: () -> Unit) {
    var open by remember { mutableStateOf<Card?>(null) }
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("All") }
    val current = open
    BackHandler(enabled = current != null) { open = null }
    if (current != null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            TopRow(current.title, onBack = { open = null })
            LazyColumn { item { Blocks(current.blocks) } }
        }
        return
    }
    val kinds = listOf("All") + cards.map { cardKindName(it.kind) }.distinct()
    val shown = cards.filter {
        (kind == "All" || cardKindName(it.kind) == kind) &&
            (query.isBlank() || it.title.contains(query, ignoreCase = true) || it.blocks.any { b -> b.text.contains(query, ignoreCase = true) || b.items.any { i -> i.contains(query, ignoreCase = true) } })
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TopRow("Emergency cards (" + cards.size + ")", onBack)
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search the cards") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        ChipRow(kinds, kind) { kind = it }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (shown.isEmpty()) item { Text("No card matches.", color = Muted) }
            items(shown, key = { it.id }) { c ->
                Tile(onClick = { open = c }) {
                    Text(c.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(cardKindName(c.kind), fontSize = 12.sp, color = Muted)
                }
            }
        }
    }
}

// ---------- academy ----------

@Composable
fun AcademyScreen(courses: List<Course>, progress: LearnProgress, onBack: () -> Unit) {
    var course by remember { mutableStateOf<Course?>(null) }
    var lesson by remember { mutableStateOf<Lesson?>(null) }
    var tick by remember { mutableStateOf(0) } // changes when a score is saved, so the lists redraw
    BackHandler(enabled = lesson != null || course != null) {
        if (lesson != null) lesson = null else course = null
    }
    val l = lesson
    val c = course
    if (c != null && l != null) {
        LessonView(l, progress, onBack = { lesson = null }, onScored = { tick++ })
        return
    }
    if (c != null) {
        @Suppress("UNUSED_EXPRESSION") tick
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            TopRow(c.title, onBack = { course = null })
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (c.summary.isNotBlank()) item { Text(c.summary, color = Muted, fontSize = 14.sp) }
                for (m in c.modules) {
                    item { Text(m.title, fontFamily = HeadingFont, fontSize = 16.sp, color = Blue, modifier = Modifier.padding(top = 10.dp)) }
                    items(m.lessons, key = { it.id }) { ls ->
                        Tile(onClick = { lesson = ls }) {
                            val best = progress.best(ls.id)
                            Text(ls.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(
                                ls.minutes.toString() + " min" + (if (ls.quiz.isNotEmpty()) " · quiz" else "") +
                                    (if (best >= 0) (if (ls.quiz.isNotEmpty()) " · best " + best + "%" else " · read") else ""),
                                fontSize = 12.sp, color = Muted,
                            )
                        }
                    }
                }
            }
        }
        return
    }
    @Suppress("UNUSED_EXPRESSION") tick
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TopRow("Academy", onBack)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (tier in listOf(3, 2, 1, 0)) {
                val list = courses.filter { (if (it.tier in 1..3) it.tier else 0) == tier }.sortedBy { it.title.lowercase() }
                if (list.isEmpty()) continue
                item { Text(tierName(tier), fontFamily = HeadingFont, fontSize = 17.sp, color = Blue, modifier = Modifier.padding(top = 10.dp)) }
                items(list, key = { it.id }) { co ->
                    Tile(onClick = { course = co }) {
                        val done = co.modules.sumOf { m -> m.lessons.count { progress.done(it.id) } }
                        Text(co.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(co.lessonCount.toString() + " lessons · about " + (co.minutes / 60.0).let { String.format("%.1f", it) } + " hours · " + done + " done", fontSize = 12.sp, color = Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonView(lesson: Lesson, progress: LearnProgress, onBack: () -> Unit, onScored: () -> Unit) {
    var answers by remember(lesson.id) { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    // a lesson with no quiz counts as read once it is opened
    LaunchedEffect(lesson.id) {
        if (lesson.quiz.isEmpty()) {
            progress.record(lesson.id, 100)
            onScored()
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TopRow(lesson.title, onBack)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Blocks(lesson.blocks) }
            if (lesson.source.isNotBlank()) item { Text("Source: " + lesson.source, fontSize = 12.sp, color = Muted) }
            if (lesson.quiz.isNotEmpty()) {
                item { Text("Check yourself", fontFamily = HeadingFont, fontSize = 18.sp, color = Blue, modifier = Modifier.padding(top = 12.dp)) }
                lesson.quiz.forEachIndexed { qi, q ->
                    item {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(q.prompt, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            val picked = answers[qi]
                            // the answers are shown in a fixed shuffled order so the right one is not always first
                            val order = q.options.indices.sortedBy { (it * 7 + qi * 3 + q.prompt.length) % 11 }
                            for (oi in order) {
                                val mark = when {
                                    picked == null -> ""
                                    oi == q.correct -> "  ✓"
                                    oi == picked -> "  ✗"
                                    else -> ""
                                }
                                Text(
                                    q.options[oi] + mark,
                                    color = when {
                                        picked != null && oi == q.correct -> Blue
                                        picked == oi -> Danger
                                        else -> Bone
                                    },
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PanelHigh)
                                        .clickable(enabled = picked == null) {
                                            answers = answers + (qi to oi)
                                            if (answers.size == lesson.quiz.size) {
                                                val right = answers.count { (k, v) -> lesson.quiz[k].correct == v }
                                                progress.record(lesson.id, right * 100 / lesson.quiz.size)
                                                onScored()
                                            }
                                        }.padding(10.dp),
                                )
                            }
                            if (picked != null && q.why.isNotBlank()) Text(q.why, fontSize = 13.sp, color = Muted)
                        }
                    }
                }
                if (answers.size == lesson.quiz.size) {
                    item {
                        val right = answers.count { (k, v) -> lesson.quiz[k].correct == v }
                        val pct = right * 100 / lesson.quiz.size
                        Text("You got $right of ${lesson.quiz.size} (" + pct + "%)." + (if (pct >= 70) " Passed." else " Read it again and retry."), fontWeight = FontWeight.Bold, color = if (pct >= 70) Blue else Danger)
                    }
                    item { Btn("TRY AGAIN", { answers = emptyMap() }, Modifier.fillMaxWidth(), outlined = true) }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** The carried cards and courses, loaded once when the app opens. */
class LoadedLibrary(val cards: List<Card>, val courses: List<Course>, val progress: LearnProgress)

@Composable
fun rememberLibrary(): LoadedLibrary {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember {
        val lib = Library(AssetSource(context.assets))
        LoadedLibrary(lib.cards(), lib.courses(), LearnProgress(context))
    }
}
