package com.cindrix.pocket

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/** Which screen is showing. */
sealed class Screen {
    object Home : Screen()
    /** The camera. [quick] is set while the fast-mode popup is open over it. */
    data class Scan(val quick: BatchItem? = null) : Screen()
    object Stock : Screen()
    object Track : Screen()
    object Settings : Screen()
    object Cards : Screen()
    object Count : Screen()
    object Checkin : Screen()
    object Hub : Screen()
    object HubScan : Screen()
    object Erased : Screen()
    object Academy : Screen()
    data class Form(val item: BatchItem, val isNew: Boolean, val known: Boolean, val fromScan: Boolean) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        val store = Store(filesDir, getSharedPreferences("pocket", Context.MODE_PRIVATE))
        val state = AppState(store)
        TrackRecorder.init(filesDir)

        setContent {
            CindrixTheme {
                App(state)
            }
        }
    }
}

@Composable
fun App(state: AppState) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val library = rememberLibrary()
    var hubInvite by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    // After an item is added, ignore the same can still sitting in front of the camera.
    val ignoreUntil = remember { LongArray(1) }
    fun backToScan() {
        ignoreUntil[0] = System.currentTimeMillis() + 2000
        screen = Screen.Scan()
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (e: Exception) {
                // Some folders do not allow a lasting grant; saving may still work this session.
            }
            state.setFolder(uri.toString())
            toast("Folder set")
        }
    }

    fun deliver(name: String): Boolean {
        val folder = state.folderUri
        val text = state.outboxStore.read(name) ?: return false
        val result = when {
            folder != null -> Exporter.saveToFolder(context, Uri.parse(folder), name, text)
            Exporter.canUseDefaultFolder() -> Exporter.saveToDefault(context, name, text)
            else -> return false
        }
        return if (result.isSuccess) {
            state.delivered(name)
            true
        } else {
            toast("Could not deliver: " + (result.exceptionOrNull()?.message ?: "unknown error"))
            false
        }
    }

    // SEND TO OPS: keep the list safe in the outbox first, then deliver whatever is waiting.
    val pickStock = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val text = try {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            } catch (e: Exception) {
                null
            }
            if (text != null && state.loadStock(text)) {
                toast("Stock list loaded: " + state.stock.size + " lines")
            } else {
                toast("That file is not a Cindrix stock list")
            }
        }
    }

    val doSend: () -> Unit = {
        val queued = state.queueBatch()
        if (queued == null && state.outbox.isEmpty()) {
            toast("Nothing to send")
        } else if (state.folderUri == null && !Exporter.canUseDefaultFolder()) {
            toast("Saved in the outbox. Choose the Ops folder, then tap TO OPS FOLDER.")
            pickFolder.launch(null)
        } else {
            val waiting = state.outbox.toList()
            val ok = waiting.count { deliver(it) }
            if (ok > 0) toast("Delivered " + ok + (if (ok == 1) " list" else " lists") + " to " + (if (state.folderUri == null) Exporter.DEFAULT_PATH else "the Cindrix folder"))
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        when (val current = screen) {
            Screen.Home -> HomeScreen(
                state = state,
                onScan = { screen = Screen.Scan() },
                onManual = {
                    val made = state.newItem("")
                    screen = Screen.Form(made.first, isNew = true, known = false, fromScan = false)
                },
                onEdit = { item -> screen = Screen.Form(item, isNew = false, known = false, fromScan = false) },
                onSettings = { screen = Screen.Settings },
                onSend = doSend,
                onClear = { confirmClear = true },
                onDeliver = { name ->
                    if (state.folderUri == null && !Exporter.canUseDefaultFolder()) {
                        toast("Choose the Ops folder first")
                        pickFolder.launch(null)
                    } else if (deliver(name)) {
                        toast("Delivered to the Ops folder")
                    }
                },
                onAirLink = { name ->
                    val text = state.outboxStore.read(name)
                    if (text != null) {
                        val r = Exporter.share(context, name, text)
                        if (r.isFailure) toast("Could not send: " + (r.exceptionOrNull()?.message ?: "unknown error"))
                    }
                },
                onDiscard = { name -> state.discard(name) },
                onStock = { screen = Screen.Stock },
                onTrack = { screen = Screen.Track },
                onCards = { screen = Screen.Cards },
                onAcademy = { screen = Screen.Academy },
                onHub = { screen = Screen.Hub },
                onCount = { screen = Screen.Count },
                onCheckin = { screen = Screen.Checkin },
            )

            is Screen.Scan -> ScanScreen(
                onCode = { code ->
                    val now = screen
                    if (now is Screen.Scan && now.quick == null && System.currentTimeMillis() >= ignoreUntil[0]) {
                        Feedback.scanOk(context, state.soundOn, state.vibrateOn)
                        val made = state.newItem(code)
                        if (made.second) {
                            screen = Screen.Scan(quick = made.first)
                        } else {
                            screen = Screen.Form(made.first, isNew = true, known = made.second, fromScan = true)
                        }
                    }
                },
                onManual = {
                    val made = state.newItem("")
                    screen = Screen.Form(made.first, isNew = true, known = false, fromScan = true)
                },
                onDone = { screen = Screen.Home },
                fastMode = state.fastMode,
                onToggleFast = { state.updateFastMode(!state.fastMode) },
                quick = current.quick,
                quickOnHand = current.quick?.let { state.onHand(it.barcode) },
                quickStartUseBy = if (state.fastMode) (current.quick?.let { state.lookup(it.barcode)?.lastUseBy } ?: "") else "",
                onQuickAdd = { item ->
                    val merged = state.addOrMerge(item)
                    if (!item.remove) state.remember(item)
                    toast(if (item.remove) "Taking out " + InventoryCsv.qtyText(item.qty) + " x " + item.name else if (merged) "Added to the matching line" else "Added " + item.name)
                    backToScan()
                },
                onQuickAddMore = { item ->
                    // Same product, a different use-by date (for example a case with two date codes).
                    state.addOrMerge(item)
                    state.remember(item)
                    toast("Added " + InventoryCsv.qtyText(item.qty) + " x " + item.name + ". Next date:")
                    screen = Screen.Scan(quick = item.copy(scanId = java.util.UUID.randomUUID().toString(), qty = 1.0))
                },
                onQuickEdit = { item ->
                    screen = Screen.Form(item, isNew = true, known = true, fromScan = true)
                },
                onQuickCancel = { backToScan() },
            )

            is Screen.Form -> FormScreen(
                initial = current.item,
                isNew = current.isNew,
                known = current.known,
                onSave = { item ->
                    if (current.isNew) {
                        val merged = state.addOrMerge(item)
                        toast(if (merged) "Added to the matching line" else "Added")
                    } else {
                        state.update(current.item, item)
                    }
                    state.remember(item)
                    state.rememberLocation(item.location)
                    if (current.fromScan) backToScan() else screen = Screen.Home
                },
                onDelete = if (current.isNew) null else ({
                    state.remove(current.item)
                    screen = Screen.Home
                }),
                onCancel = { if (current.fromScan) backToScan() else screen = Screen.Home },
            )

            Screen.Track -> TrackScreen(
                state = state,
                onSaved = { name ->
                    toast("Saved to the outbox: " + name)
                    screen = Screen.Home
                },
                onBack = { screen = Screen.Home },
            )

            Screen.Hub -> HubScreen(
                state = state,
                onScanQr = { screen = Screen.HubScan },
                onErased = { screen = Screen.Erased },
                invite = hubInvite,
                onInviteUsed = { hubInvite = null },
                onBack = { screen = Screen.Home },
            )

            Screen.HubScan -> HubScanScreen(
                onInvite = { text ->
                    hubInvite = text
                    screen = Screen.Hub
                },
                onBack = { screen = Screen.Hub },
            )

            Screen.Erased -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text("This phone was erased by the Hub.", fontFamily = HeadingFont, fontSize = 22.sp)
                Spacer(Modifier.height(8.dp))
                Text("Its cards progress, outbox and saved settings are gone. To use it with the Hub again, ask for a new invite.")
                Spacer(Modifier.height(16.dp))
                Btn("OK", { screen = Screen.Home }, Modifier.fillMaxWidth())
            }

            Screen.Count -> CountScreen(
                state = state,
                onSaved = { name -> toast("Saved to the outbox: " + name); screen = Screen.Home },
                onBack = { screen = Screen.Home },
            )

            Screen.Checkin -> CheckinScreen(
                state = state,
                onSaved = { name -> toast("Saved to the outbox: " + name); screen = Screen.Home },
                onBack = { screen = Screen.Home },
            )

            Screen.Cards -> CardsScreen(cards = library.cards, onBack = { screen = Screen.Home })

            Screen.Academy -> AcademyScreen(courses = library.courses, progress = library.progress, onBack = { screen = Screen.Home })

            Screen.Stock -> InStockScreen(
                state = state,
                onLoad = { pickStock.launch(arrayOf("text/*", "application/octet-stream", "*/*")) },
                onBack = { screen = Screen.Home },
            )

            Screen.Settings -> SettingsScreen(
                state = state,
                onPickFolder = { pickFolder.launch(null) },
                onBack = { screen = Screen.Home },
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the batch?") },
            text = { Text("This removes all " + state.batch.size + " lines that have not been exported.") },
            confirmButton = {
                TextButton(onClick = {
                    state.clear()
                    confirmClear = false
                }) { Text("CLEAR") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("CANCEL") }
            },
        )
    }
}
