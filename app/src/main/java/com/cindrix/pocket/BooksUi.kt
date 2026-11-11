package com.cindrix.pocket

import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Books on the phone, and (when joined to the Hub) the Hub's shelf to pick more from. */
@Composable
fun BooksScreen(state: AppState, onBack: () -> Unit) {
    var open by remember { mutableStateOf<SavedBook?>(null) }
    val current = open
    if (current != null) {
        BookReader(current, onBack = { open = null })
        return
    }
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableStateOf(0) }
    val shelf = remember { mutableStateListOf<ShelfBook>() }
    var status by remember { mutableStateOf("") }
    var busyId by remember { mutableStateOf("") }
    @Suppress("UNUSED_EXPRESSION") tick
    val mine = state.books.list()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("BACK", fontFamily = HeadingFont) }
            Text("Books", fontFamily = HeadingFont, fontSize = 18.sp)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("On this phone (" + mine.size + ")", fontFamily = HeadingFont, color = Blue, fontSize = 16.sp) }
            if (mine.isEmpty()) item { Text("Nothing saved yet. Join the Hub and pick books to keep on the phone.", color = Muted) }
            items(mine, key = { it.id }) { b ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
                    Text(b.title, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { open = b })
                    Row {
                        TextButton(onClick = { open = b }) { Text("READ") }
                        TextButton(onClick = { state.books.remove(b.id); tick++ }) { Text("REMOVE") }
                    }
                }
            }
            if (state.linked) {
                item { Text("On the Hub", fontFamily = HeadingFont, color = Blue, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp)) }
                item {
                    Btn("LOAD THE HUB'S BOOK LIST", {
                        val base = state.hubBase ?: return@Btn
                        val token = state.hubToken ?: return@Btn
                        scope.launch {
                            useWifiForHub(context)
                            val r = withContext(Dispatchers.IO) { HubLink.shelfList(base, token) }
                            r.onSuccess { shelf.clear(); shelf.addAll(it.sortedBy { b -> b.title.lowercase() }); status = it.size.toString() + " books you can keep (PDF, Markdown, text)." }
                            r.onFailure { status = it.message ?: "Could not load the list." }
                        }
                    }, Modifier.fillMaxWidth(), outlined = true)
                }
                items(shelf.filter { s -> mine.none { it.id == s.id } }, key = { it.id }) { s ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
                        Text(s.title, fontWeight = FontWeight.Bold)
                        Text((if (s.author.isNotBlank()) s.author + " · " else "") + s.kind.uppercase(), fontSize = 12.sp, color = Muted)
                        TextButton(enabled = busyId.isEmpty(), onClick = {
                            val base = state.hubBase ?: return@TextButton
                            val token = state.hubToken ?: return@TextButton
                            busyId = s.id
                            status = "Downloading " + s.title + "..."
                            scope.launch {
                                useWifiForHub(context)
                                val dest = state.books.fileFor(s.id, s.kind)
                                val r = withContext(Dispatchers.IO) { HubLink.shelfRead(base, token, s.id, dest) }
                                busyId = ""
                                if (r.isSuccess) { state.books.register(s.id, s.title, s.kind); tick++; status = "Saved " + s.title + "." }
                                else status = r.exceptionOrNull()?.message ?: "Could not download."
                            }
                        }) { Text("KEEP ON THIS PHONE") }
                    }
                }
            }
        }
        if (status.isNotBlank()) Text(status, color = Muted, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun BookReader(book: SavedBook, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("BACK", fontFamily = HeadingFont) }
            Text(book.title, fontFamily = HeadingFont, fontSize = 17.sp, maxLines = 2)
        }
        if (book.kind == "pdf") PdfPages(book) else TextBook(book)
    }
}

@Composable
private fun TextBook(book: SavedBook) {
    val text = remember(book.id) { try { book.file.readText(Charsets.UTF_8) } catch (_: Exception) { "" } }
    val blocks = remember(book.id) {
        if (book.kind == "md") Md.blocks(Md.front(text).second)
        else text.replace("\r\n", "\n").split(Regex("\n\\s*\n")).filter { it.isNotBlank() }.map { Block(BlockKind.PARAGRAPH, it.trim().replace("\n", " ")) }
    }
    if (blocks.isEmpty()) { Text("This book could not be read.", color = Danger); return }
    LazyColumn { item { Blocks(blocks) } }
}

@Composable
private fun PdfPages(book: SavedBook) {
    var page by remember(book.id) { mutableStateOf(0) }
    val holder = remember(book.id) {
        try {
            val fd = ParcelFileDescriptor.open(book.file, ParcelFileDescriptor.MODE_READ_ONLY)
            fd to PdfRenderer(fd)
        } catch (_: Exception) { null }
    }
    DisposableEffect(book.id) { onDispose { try { holder?.second?.close(); holder?.first?.close() } catch (_: Exception) {} } }
    if (holder == null) { Text("This PDF could not be opened.", color = Danger); return }
    val renderer = holder.second
    val total = renderer.pageCount
    val bitmap = remember(book.id, page) {
        renderer.openPage(page).use { p ->
            val w = 1100
            val h = (w.toFloat() * p.height / p.width).toInt().coerceAtLeast(1)
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
                b.eraseColor(AColor.WHITE)
                p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(enabled = page > 0, onClick = { page-- }) { Text("PREVIOUS") }
        Text("Page " + (page + 1) + " of " + total, modifier = Modifier.weight(1f))
        TextButton(enabled = page < total - 1, onClick = { page++ }) { Text("NEXT") }
    }
    Spacer(Modifier.height(4.dp))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Image(bitmap.asImageBitmap(), contentDescription = "Page " + (page + 1), modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
    }
}
