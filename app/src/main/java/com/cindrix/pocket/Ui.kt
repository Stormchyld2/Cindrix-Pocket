package com.cindrix.pocket

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// ---------------------------------------------------------------- shared pieces

@Composable
fun BrandHeader(subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.pocket_logo), contentDescription = null, modifier = Modifier.size(44.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Row {
                Text("CINDRIX ", fontFamily = HeadingFont, fontSize = 26.sp, color = MaterialTheme.colorScheme.onBackground)
                Text("POCKET", fontFamily = HeadingFont, fontSize = 26.sp, color = MaterialTheme.colorScheme.primary)
            }
            Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun Btn(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outlined: Boolean = false,
) {
    if (outlined) {
        OutlinedButton(onClick = onClick, modifier = modifier.height(52.dp), enabled = enabled, shape = Round) {
            Text(text, fontFamily = HeadingFont, fontSize = 15.sp)
        }
    } else {
        Button(onClick = onClick, modifier = modifier.height(52.dp), enabled = enabled, shape = Round) {
            Text(text, fontFamily = HeadingFont, fontSize = 15.sp)
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, fontFamily = HeadingFont, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(option) },
            )
        }
    }
}

// ---------------------------------------------------------------- home

@Composable
fun HomeScreen(
    state: AppState,
    onScan: () -> Unit,
    onManual: () -> Unit,
    onEdit: (BatchItem) -> Unit,
    onSettings: () -> Unit,
    onSend: () -> Unit,
    onClear: () -> Unit,
    onDeliver: (String) -> Unit,
    onAirLink: (String) -> Unit,
    onDiscard: (String) -> Unit,
    onStock: () -> Unit,
    onTrack: () -> Unit,
    onCards: () -> Unit,
    onAcademy: () -> Unit,
    onHub: () -> Unit,
) {
    val hasItems = state.batch.isNotEmpty()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                BrandHeader(state.source() + "  |  " + state.batch.size + " in batch")
            }
            TextButton(onClick = onSettings) { Text("SETTINGS", fontFamily = HeadingFont) }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Btn("SCAN", onScan, Modifier.weight(1f))
            Btn("ADD BY HAND", onManual, Modifier.weight(1f), outlined = true)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Btn("CARDS", onCards, Modifier.weight(1f))
            Btn("ACADEMY", onAcademy, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Btn(if (state.linked) "HUB VIEW (JOINED)" else "HUB: JOIN WITH QR", onHub, Modifier.fillMaxWidth(), outlined = true)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Btn("IN STOCK (" + state.stock.size + ")", onStock, Modifier.weight(1f), outlined = true)
            Btn(if (TrackRecorder.recording) "TRACK (ON)" else "TRACK", onTrack, Modifier.weight(1f), outlined = true)
        }
        Spacer(Modifier.height(12.dp))
        if (!hasItems) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "No items yet.\nTap SCAN and point the camera at a barcode.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.batch, key = { it.scanId }) { item ->
                    ItemRow(item) { onEdit(item) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (state.outbox.isNotEmpty()) {
            SectionLabel("WAITING TO REACH THE HUB (" + state.outbox.size + ")")
            state.outbox.forEach { name ->
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(10.dp)
                ) {
                    Text(name, fontSize = 12.sp, color = Bone)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onDeliver(name) }) { Text("TO THE FOLDER") }
                        TextButton(onClick = { onAirLink(name) }) { Text("AIRLINK") }
                        TextButton(onClick = { onDiscard(name) }) { Text("DELETE", color = Danger) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Btn(if (hasItems) "SEND TO OPS" else "SEND WAITING FILES", onSend, Modifier.fillMaxWidth(), enabled = hasItems || state.outbox.isNotEmpty())
        TextButton(onClick = onClear, enabled = hasItems, modifier = Modifier.align(Alignment.End)) {
            Text("CLEAR BATCH", fontFamily = HeadingFont)
        }
    }
}

@Composable
fun ItemRow(item: BatchItem, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Text(
            (if (item.remove) "TAKE OUT  " else "") + item.name,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = if (item.remove) Danger else Bone,
        )
        val bits = listOfNotNull(
            (if (item.remove) "-" else "") + InventoryCsv.qtyText(item.qty) + " " + item.unit,
            if (item.useBy.isNotEmpty()) "use by " + item.useBy else null,
            if (item.category.isNotEmpty()) item.category else null,
            if (item.location.isNotEmpty()) item.location else null,
        )
        Text(bits.joinToString("  |  "), fontSize = 13.sp, color = Bone.copy(alpha = 0.8f))
    }
}

// ---------------------------------------------------------------- scan

@Composable
fun ScanScreen(
    onCode: (String) -> Unit,
    onManual: () -> Unit,
    onDone: () -> Unit,
    fastMode: Boolean,
    onToggleFast: () -> Unit,
    quick: BatchItem?,
    quickStartUseBy: String,
    quickOnHand: Pair<Double, String>?,
    onQuickAdd: (BatchItem) -> Unit,
    onQuickAddMore: (BatchItem) -> Unit,
    onQuickEdit: (BatchItem) -> Unit,
    onQuickCancel: () -> Unit,
) {
    BackHandler(onBack = onDone)
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    var torch by remember { mutableStateOf(false) }
    var hasTorch by remember { mutableStateOf(false) }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Scan needs the camera to read barcodes. Nothing is recorded or sent anywhere.",
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Btn("ALLOW CAMERA", { launcher.launch(Manifest.permission.CAMERA) }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Btn("ADD BY HAND", onManual, Modifier.fillMaxWidth(), outlined = true)
            Spacer(Modifier.height(8.dp))
            Btn("BACK", onDone, Modifier.fillMaxWidth(), outlined = true)
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        CameraView(torchOn = torch, onCode = onCode, onTorchSupport = { hasTorch = it })
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.85f)
                .height(140.dp)
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
        )
        Text(
            if (fastMode) "Fast mode: known items only ask quantity and use-by" else "Line the barcode up inside the box",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x99000000))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("DONE", onDone, Modifier.weight(1f))
                Btn("BY HAND", onManual, Modifier.weight(1f), outlined = true)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn(
                    if (fastMode) "FAST MODE: ON" else "FAST MODE: OFF",
                    onToggleFast,
                    Modifier.weight(1f),
                    outlined = !fastMode,
                )
                if (hasTorch) {
                    Btn(if (torch) "LIGHT ON" else "LIGHT", { torch = !torch }, Modifier.weight(1f), outlined = true)
                }
            }
        }
    }

    if (quick != null) {
        QuickAddDialog(
            item = quick,
            startUseBy = quickStartUseBy,
            onHand = quickOnHand,
            onAdd = onQuickAdd,
            onAddMore = onQuickAddMore,
            onEditAll = onQuickEdit,
            onCancel = onQuickCancel,
        )
    }
}

/** Fast mode popup for an item the app already knows: quantity and use-by date, nothing else. */
@Composable
fun QuickAddDialog(
    item: BatchItem,
    startUseBy: String,
    onHand: Pair<Double, String>?,
    onAdd: (BatchItem) -> Unit,
    onAddMore: (BatchItem) -> Unit,
    onEditAll: (BatchItem) -> Unit,
    onCancel: () -> Unit,
) {
    var qtyText by remember(item.scanId) { mutableStateOf("1") }
    var useBy by remember(item.scanId) { mutableStateOf(startUseBy) }
    var picking by remember(item.scanId) { mutableStateOf(false) }
    var removing by remember(item.scanId) { mutableStateOf(item.remove) }

    val qty: Double? = qtyText.toDoubleOrNull()
    val valid = qty != null && qty > 0.0
    val today = LocalDate.now()

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (onHand != null) {
                    Text(
                        "On hand: " + InventoryCsv.qtyText(onHand.first) + " " + item.unit +
                            (if (onHand.second.isNotEmpty()) "  |  soonest use-by " + onHand.second else ""),
                        color = Blue,
                        fontSize = 14.sp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !removing, onClick = { removing = false }, label = { Text("ADD STOCK") })
                    FilterChip(selected = removing, onClick = { removing = true }, label = { Text("TAKE OUT") })
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Btn("-", {
                        val current = qtyText.toDoubleOrNull() ?: 0.0
                        qtyText = InventoryCsv.qtyText(if (current > 1.0) current - 1.0 else 0.0)
                    }, Modifier.width(56.dp), outlined = true)
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { text -> qtyText = text.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Qty (" + item.unit + ")") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    Btn("+", {
                        val current = qtyText.toDoubleOrNull() ?: 0.0
                        qtyText = InventoryCsv.qtyText(current + 1.0)
                    }, Modifier.width(56.dp), outlined = true)
                }
                if (!removing) SectionLabel("USE BY: " + if (useBy.isEmpty()) "none" else useBy)
                if (!removing) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (startUseBy.isNotEmpty()) {
                        FilterChip(
                            selected = useBy == startUseBy,
                            onClick = { useBy = startUseBy },
                            label = { Text("Same as last") },
                        )
                    }
                    FilterChip(selected = useBy.isEmpty(), onClick = { useBy = "" }, label = { Text("None") })
                    FilterChip(selected = false, onClick = { useBy = today.plusMonths(6).toString() }, label = { Text("+6 mo") })
                    FilterChip(selected = false, onClick = { useBy = today.plusYears(1).toString() }, label = { Text("+1 yr") })
                    FilterChip(selected = false, onClick = { useBy = today.plusYears(2).toString() }, label = { Text("+2 yr") })
                    FilterChip(selected = false, onClick = { useBy = today.plusYears(5).toString() }, label = { Text("+5 yr") })
                    FilterChip(selected = false, onClick = { picking = true }, label = { Text("Pick date") })
                }
                if (!removing) TextButton(onClick = { onAddMore(item.copy(qty = qty ?: 1.0, useBy = useBy)) }, enabled = valid) {
                    Text("ADD, THEN ANOTHER USE-BY DATE")
                }
                if (!removing) TextButton(onClick = { onEditAll(item.copy(qty = qty ?: 1.0, useBy = useBy)) }) {
                    Text("EDIT ALL DETAILS")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (removing) onAdd(item.copy(qty = qty ?: 1.0, useBy = "", remove = true))
                    else onAdd(item.copy(qty = qty ?: 1.0, useBy = useBy, remove = false))
                },
                enabled = valid,
            ) {
                Text(if (removing) "TAKE OUT" else "ADD")
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("CANCEL") } },
    )

    if (picking) {
        UseByPicker(onDate = { useBy = it }, onDismiss = { picking = false })
    }
}

// ---------------------------------------------------------------- item form

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UseByPicker(onDate: (String) -> Unit, onDismiss: () -> Unit) {
    val pickerState = rememberDatePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = pickerState.selectedDateMillis
                if (millis != null) {
                    onDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = pickerState)
    }
}

@Composable
fun FormScreen(
    initial: BatchItem,
    isNew: Boolean,
    known: Boolean,
    onSave: (BatchItem) -> Unit,
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)

    var name by remember(initial.scanId) { mutableStateOf(initial.name) }
    var qtyText by remember(initial.scanId) { mutableStateOf(InventoryCsv.qtyText(initial.qty)) }
    var unit by remember(initial.scanId) { mutableStateOf(initial.unit) }
    var useBy by remember(initial.scanId) { mutableStateOf(initial.useBy) }
    var category by remember(initial.scanId) { mutableStateOf(initial.category) }
    var location by remember(initial.scanId) { mutableStateOf(initial.location) }
    var picking by remember(initial.scanId) { mutableStateOf(false) }

    val qty: Double? = qtyText.toDoubleOrNull()
    val valid = name.isNotBlank() && qty != null && qty > 0.0

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BrandHeader(if (isNew) "New item" else "Edit item")

        if (initial.barcode.isNotEmpty()) {
            Text(
                "Barcode " + initial.barcode + if (known) "  |  known item" else "  |  new to the catalog",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Item name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Btn("-", {
                val current = qtyText.toDoubleOrNull() ?: 0.0
                qtyText = InventoryCsv.qtyText(if (current > 1.0) current - 1.0 else 0.0)
            }, Modifier.width(60.dp), outlined = true)
            OutlinedTextField(
                value = qtyText,
                onValueChange = { text -> qtyText = text.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Quantity") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            Btn("+", {
                val current = qtyText.toDoubleOrNull() ?: 0.0
                qtyText = InventoryCsv.qtyText(current + 1.0)
            }, Modifier.width(60.dp), outlined = true)
        }

        SectionLabel("UNIT")
        ChipRow(Units.all, unit) { unit = it }

        SectionLabel("USE BY: " + if (useBy.isEmpty()) "none" else useBy)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val today = LocalDate.now()
            FilterChip(selected = false, onClick = { useBy = "" }, label = { Text("None") })
            FilterChip(selected = false, onClick = { useBy = today.plusMonths(6).toString() }, label = { Text("+6 mo") })
            FilterChip(selected = false, onClick = { useBy = today.plusYears(1).toString() }, label = { Text("+1 yr") })
            FilterChip(selected = false, onClick = { useBy = today.plusYears(2).toString() }, label = { Text("+2 yr") })
            FilterChip(selected = false, onClick = { useBy = today.plusYears(5).toString() }, label = { Text("+5 yr") })
            FilterChip(selected = false, onClick = { picking = true }, label = { Text("Pick date") })
        }

        SectionLabel("CATEGORY (OPTIONAL)")
        ChipRow(Categories.all, category) { category = if (category == it) "" else it }

        OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            label = { Text("Location (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Btn(
            "SAVE",
            {
                onSave(
                    initial.copy(
                        name = name.trim(),
                        qty = qty ?: 1.0,
                        unit = unit,
                        useBy = useBy,
                        category = category,
                        location = location.trim(),
                    )
                )
            },
            Modifier.fillMaxWidth(),
            enabled = valid,
        )
        Btn("CANCEL", onCancel, Modifier.fillMaxWidth(), outlined = true)
        if (onDelete != null) {
            Btn("DELETE THIS LINE", onDelete, Modifier.fillMaxWidth(), outlined = true)
        }
    }

    if (picking) {
        UseByPicker(onDate = { useBy = it }, onDismiss = { picking = false })
    }
}

// ---------------------------------------------------------------- settings

@Composable
fun SettingsScreen(state: AppState, onPickFolder: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    val folder = state.folderUri
    val folderLabel = if (folder == null) {
        if (Exporter.canUseDefaultFolder()) Exporter.DEFAULT_PATH + "  (made for you)" else "Not chosen yet"
    } else {
        (Uri.parse(folder).lastPathSegment ?: folder).replace(':', '/')
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BrandHeader("Settings")

        SectionLabel("DEVICE CALLSIGN")
        OutlinedTextField(
            value = state.callsign,
            onValueChange = { state.updateCallsign(it) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "This name goes into every export so Ops knows which phone the items came from.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Btn("NEW RANDOM CALLSIGN", { state.updateCallsign(Callsign.generate()) }, Modifier.fillMaxWidth(), outlined = true)

        SectionLabel("WHEN A BARCODE IS READ")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Beep", Modifier.weight(1f))
            Switch(checked = state.soundOn, onCheckedChange = { state.updateSound(it) })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Vibrate", Modifier.weight(1f))
            Switch(checked = state.vibrateOn, onCheckedChange = { state.updateVibrate(it) })
        }
        Text(
            "The beep follows the phone's silent mode. Fast mode is switched on and off on the scan screen.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionLabel("CINDRIX FOLDER")
        Text(folderLabel)
        Text(
            "Finished lists go here on their own, nothing to set up. Cindrix AirLink or a file manager can pick them up from this folder. Only choose a different folder if Ops needs it somewhere else (for example its user folder). Until a list is delivered it waits safely in the outbox.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Btn(if (Exporter.canUseDefaultFolder()) "CHOOSE A DIFFERENT FOLDER" else "CHOOSE FOLDER", onPickFolder, Modifier.fillMaxWidth(), outlined = Exporter.canUseDefaultFolder())
        if (folder != null && Exporter.canUseDefaultFolder()) {
            Btn("GO BACK TO THE AUTOMATIC FOLDER", { state.setFolder(null) }, Modifier.fillMaxWidth(), outlined = true)
        }

        Btn("BACK", onBack, Modifier.fillMaxWidth(), outlined = true)
        Text(
            "Cindrix Pocket " + version,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}


// ---------------------------------------------------------------- in stock

@Composable
fun InStockScreen(state: AppState, onLoad: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var query by remember { mutableStateOf("") }
    val shown = StockList.search(state.stock.toList(), query).sortedBy { it.name.lowercase() }
    val updated = state.stock.firstOrNull()?.updated ?: ""
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BrandHeader("In stock")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search name, place or category") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (state.stock.isEmpty()) "No stock list yet. Load the file Ops makes (outbox/stock-list.csv)."
            else "A read-only copy from Ops" + (if (updated.isNotEmpty()) ", made " + updated.replace('T', ' ').removeSuffix("Z") + " UTC" else "") + ". Only the Ops host changes the real inventory.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown) { line ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface).padding(12.dp)
                ) {
                    Text(line.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Bone)
                    Text(
                        listOfNotNull(
                            InventoryCsv.qtyText(line.qty) + " " + line.unit,
                            line.location.ifEmpty { null },
                            if (line.useBy.isNotEmpty()) "use by " + line.useBy else null,
                        ).joinToString("  |  "),
                        fontSize = 13.sp,
                        color = Bone.copy(alpha = 0.8f),
                    )
                }
            }
        }
        Btn("LOAD STOCK LIST FROM OPS", onLoad, Modifier.fillMaxWidth())
        Btn("BACK", onBack, Modifier.fillMaxWidth(), outlined = true)
    }
}
