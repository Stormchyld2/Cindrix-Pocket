package com.cindrix.pocket

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ask. Joined to the Hub: the Hub's assistant writes an answer from its books and archives. Always: the cards, lessons,
 * plans and saved books on this phone are searched too, so it still helps with no Hub (it finds the pages; it writes no summary).
 */
@Composable
fun AskScreen(state: AppState, library: LoadedLibrary, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var question by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var asked by remember { mutableStateOf("") }
    var hubText by remember { mutableStateOf("") }
    var hubNote by remember { mutableStateOf("") }
    var hubSources by remember { mutableStateOf<List<String>>(emptyList()) }
    val hits = remember { mutableStateListOf<Hit>() }
    var open by remember { mutableStateOf<Hit?>(null) }
    val history = remember { ArrayList<String>() }

    val current = open
    BackHandler { if (current != null) open = null else onBack() }
    if (current != null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { open = null }) { Text("BACK", fontFamily = HeadingFont) }
                Text(current.kind + ": " + current.title, fontFamily = HeadingFont, fontSize = 18.sp, maxLines = 2, modifier = Modifier.weight(1f))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { Blocks(current.blocks) }
        }
        return
    }

    fun ask() {
        val q = question.trim()
        if (q.isEmpty() || busy) return
        busy = true
        asked = q
        hubText = ""
        hubNote = ""
        hubSources = emptyList()
        hits.clear()
        val before = history.toList()
        scope.launch {
            // what is on the phone answers at once, with or without the Hub
            val found = withContext(Dispatchers.IO) {
                val books = state.books.list().filter { it.kind == "md" || it.kind == "txt" }.mapNotNull { b ->
                    try { b.title to b.file.readText(Charsets.UTF_8) } catch (_: Exception) { null }
                }
                val plans = library.plans
                LocalSearch.search(q, library.cards, library.courses, plans, books)
            }
            hits.addAll(found)
            val base = state.hubBase
            val token = state.hubToken
            if (base != null && token != null) {
                hubNote = "Asking the Hub…"
                useWifiForHub(context)
                val r = withContext(Dispatchers.IO) { HubLink.ask(base, token, q, before) { text -> hubText = text } }
                r.onSuccess { a ->
                    hubText = a.text.ifBlank { a.excerpts.joinToString("\n\n") }
                    hubSources = a.sources
                    hubNote = if (hubText.isBlank()) "The Hub found nothing for that." else ""
                }
                r.onFailure { hubNote = (it.message ?: "The Hub could not answer.") + " Showing what is on this phone." }
            }
            history.add(q)
            while (history.size > 4) history.removeAt(0)
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("BACK", fontFamily = HeadingFont) }
            Text("Ask", fontFamily = HeadingFont, fontSize = 18.sp)
        }
        Text(
            if (state.linked) "Joined to the Hub: it writes an answer from its books and archives. This phone's own pages are listed under it."
            else "No Hub joined: this searches the cards, lessons, plans and books on this phone and shows you the pages.",
            color = Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp),
        )
        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            label = { Text("What do you need to know?") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { ask() }),
        )
        Btn(if (busy) "WORKING…" else "ASK", { ask() }, Modifier.fillMaxWidth().padding(vertical = 8.dp), enabled = !busy && question.isNotBlank())
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hubNote.isNotBlank()) item { Text(hubNote, color = Muted) }
            if (hubText.isNotBlank()) item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
                    Text("From the Hub", fontFamily = HeadingFont, color = Blue, fontSize = 15.sp)
                    Text(rich(hubText), fontSize = 16.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 4.dp))
                    if (hubSources.isNotEmpty()) Text("Sources: " + hubSources.joinToString(", "), fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (asked.isNotBlank() && !busy) {
                item { Text(if (hits.isEmpty()) "Nothing on this phone matches that." else "On this phone (" + hits.size + ")", fontFamily = HeadingFont, color = Blue, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp)) }
            }
            items(hits.toList()) { h ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).clickable { open = h }.padding(12.dp)) {
                    Text(h.title, fontWeight = FontWeight.Bold)
                    Text(h.kind.uppercase(), fontSize = 11.sp, color = Muted)
                    Text(h.snippet, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
