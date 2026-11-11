package com.cindrix.pocket

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.OffsetDateTime

/** Weekly counts: type what you counted next to each line, then save one count sheet to the outbox. */
@Composable
fun CountScreen(state: AppState, onSaved: (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var query by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf("") }
    val counted = remember { mutableStateMapOf<String, String>() }
    val countable = state.stock.filter { it.uid.isNotBlank() }
    val shown = StockList.search(countable, query)
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("BACK", fontFamily = HeadingFont) }
            Text("Count the stock", fontFamily = HeadingFont, fontSize = 18.sp)
        }
        if (countable.isEmpty()) {
            Text(
                if (state.stock.isEmpty()) "Load the stock list first (IN STOCK on the home screen). It comes from the Hub."
                else "This stock list has no item ids. Ask the Hub to save the inventory once so its newer list carries them, then load it again.",
                color = Muted,
            )
            return
        }
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Find an item") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.uid }) { l ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(l.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(l.location + " · Hub says " + (if (l.qty == Math.floor(l.qty)) l.qty.toLong().toString() else l.qty.toString()) + " " + l.unit, fontSize = 12.sp, color = Muted)
                    }
                    OutlinedTextField(
                        value = counted[l.uid] ?: "",
                        onValueChange = { counted[l.uid] = it },
                        label = { Text("Counted") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(110.dp),
                    )
                }
            }
        }
        if (problem.isNotBlank()) Text(problem, color = Danger)
        Spacer(Modifier.height(8.dp))
        val entered = counted.values.count { it.toDoubleOrNull() != null }
        Btn("SAVE COUNT SHEET ($entered)", {
            val lines = countable.mapNotNull { l ->
                val n = counted[l.uid]?.trim()?.toDoubleOrNull()
                if (n != null && n >= 0) CountLine(l.uid, l.qty, n) else null
            }
            if (lines.isEmpty()) {
                problem = "Type at least one counted number first."
            } else {
                val now = OffsetDateTime.now().withSecond(0).withNano(0)
                val name = Sheets.countName(state.deviceCode(), now.toEpochSecond())
                if (state.outboxStore.add(name, Sheets.countSheet(state.deviceCode(), state.source(), now, lines))) {
                    state.refreshOutbox()
                    onSaved(name)
                } else problem = "The sheet could not be saved. It is not lost: try again."
            }
        }, Modifier.fillMaxWidth(), enabled = entered > 0)
    }
}

/** Check-ins about someone who is already out: contact, checkpoint or note. Saved as one sheet in the outbox. */
@Composable
fun CheckinScreen(state: AppState, onSaved: (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var outing by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("contact") }
    var status by remember { mutableStateOf("ok") }
    var checkpoint by remember { mutableStateOf("") }
    var words by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf("") }
    val lines = remember { mutableStateListOfLines() }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("BACK", fontFamily = HeadingFont) }
            Text("Check in", fontFamily = HeadingFont, fontSize = 18.sp)
        }
        Text("For someone who is already out. The Hub adds these to the outing log as reported, after the host accepts them. This cannot start or end an outing.", color = Muted, fontSize = 13.sp)
        OutlinedTextField(value = outing, onValueChange = { outing = it }, label = { Text("Outing name or id (as the base wrote it)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        ChipRow(listOf("contact", "checkpoint", "note"), kind) { kind = it }
        if (kind != "note") ChipRow(listOf("ok", "delayed", "help"), status) { status = it }
        if (kind == "checkpoint") OutlinedTextField(value = checkpoint, onValueChange = { checkpoint = it }, label = { Text("Checkpoint name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = words, onValueChange = { words = it }, label = { Text(if (kind == "note") "Note" else "Words (optional)") }, modifier = Modifier.fillMaxWidth())
        Btn("ADD THIS CHECK-IN", {
            val o = Sheets.slug(outing)
            val now = OffsetDateTime.now().withSecond(0).withNano(0)
            val id = state.deviceCode() + "-" + now.toEpochSecond() + lines.size
            when {
                o.isEmpty() -> problem = "Say which outing this is for."
                kind == "checkpoint" && checkpoint.isBlank() -> problem = "Name the checkpoint."
                kind == "note" && words.isBlank() -> problem = "Write the note."
                else -> {
                    problem = ""
                    lines.add(
                        when (kind) {
                            "checkpoint" -> CheckinLine(id, o, kind, Sheets.created(now), checkpoint.trim(), status, words)
                            "note" -> CheckinLine(id, o, kind, Sheets.created(now), "", "", words)
                            else -> CheckinLine(id, o, kind, Sheets.created(now), status, "", words)
                        },
                    )
                    words = ""
                }
            }
        }, Modifier.fillMaxWidth(), outlined = true)
        if (problem.isNotBlank()) Text(problem, color = Danger)
        lines.forEach { Text("• " + it.kind + " " + it.a1 + " " + it.a2 + " " + it.words, fontSize = 13.sp, color = Bone) }
        Btn("SAVE SHEET (${lines.size})", {
            val now = OffsetDateTime.now().withSecond(0).withNano(0)
            val name = Sheets.checkinName(state.deviceCode(), now.toEpochSecond())
            if (state.outboxStore.add(name, Sheets.checkinSheet(state.deviceCode(), state.source(), now, lines.toList()))) {
                state.refreshOutbox()
                onSaved(name)
            } else problem = "The sheet could not be saved. It is not lost: try again."
        }, Modifier.fillMaxWidth(), enabled = lines.isNotEmpty())
    }
}

private fun mutableStateListOfLines() = androidx.compose.runtime.mutableStateListOf<CheckinLine>()
