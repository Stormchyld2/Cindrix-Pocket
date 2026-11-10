package com.cindrix.pocket

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.delay

/** Record where you walk: distance, time and climb, kept on the phone until you send it to the Hub. */
@Composable
fun TrackScreen(state: AppState, onSaved: (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val recording = TrackRecorder.recording
    val stats = TrackRecorder.stats
    var confirmDiscard by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf("") }
    var startedAt by remember { mutableLongStateOf(0L) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // the clock on screen moves every second while recording; the walk itself is kept by the service
    LaunchedEffect(recording) {
        while (recording) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }

    fun begin() {
        if (TrackRecorder.start()) {
            startedAt = System.currentTimeMillis() / 1000
            TrackService.start(context)
            problem = ""
        } else {
            problem = "A track is waiting on this phone. Save it to the outbox or throw it away first."
        }
    }

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            begin()
        } else {
            problem = "Pocket needs the phone's location to record a track. Nothing is sent anywhere."
        }
    }

    fun hasFine() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (hasFine()) {
            begin()
        } else {
            val wanted = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) wanted.add(Manifest.permission.POST_NOTIFICATIONS)
            askPermission.launch(wanted.toTypedArray())
        }
    }

    fun stop() {
        TrackService.stop(context)
        TrackRecorder.stop()
    }

    fun save() {
        val points = TrackRecorder.snapshot()
        if (points.size < 2) {
            problem = "There are not enough points to make a track yet. Walk a little further."
            return
        }
        val device = state.deviceCode()
        val id = TrackSheet.trackId(device, points.first().t)
        val name = TrackSheet.fileName(device, points.first().t)
        val text = TrackSheet.build(device, state.source(), Instant.now(), id, "", points)
        if (state.outboxStore.add(name, text)) {
            state.refreshOutbox()
            TrackRecorder.discard()
            onSaved(name)
        } else {
            problem = "The track could not be saved to the outbox. It is still on the phone."
        }
    }

    val elapsed = if (recording && startedAt > 0) (nowMs / 1000 - startedAt) else stats.seconds
    val miles = TrackMath.miles(stats.metres)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BrandHeader(if (recording) "Recording your track" else "Track")

        Text(
            String.format(Locale.US, "%.2f mi", miles),
            fontSize = 44.sp,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = HeadingFont,
        )
        Text(String.format(Locale.US, "%.2f km  |  %s  |  %d points", stats.metres / 1000.0, TrackMath.span(elapsed), stats.points))
        Text(
            String.format(Locale.US, "Up %d ft  |  Down %d ft", TrackMath.feet(stats.climb).toInt(), TrackMath.feet(stats.drop).toInt()) +
                (TrackRecorder.lastEle?.let { String.format(Locale.US, "  |  Now %d ft", TrackMath.feet(it).toInt()) } ?: ""),
        )
        Text(
            "Heights come from the phone's GPS. Up and down are good; the height above sea level can be off by tens of feet on phones older than Android 14.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (problem.isNotEmpty()) Text(problem, color = Danger)

        if (recording) {
            Btn("STOP RECORDING", { stop() }, Modifier.fillMaxWidth())
            Text(
                "The GPS keeps running with the screen off. A notice stays in the drawer while it is on.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (TrackRecorder.hasTrack) {
            Btn("SAVE TO THE OUTBOX", { save() }, Modifier.fillMaxWidth())
            Btn("KEEP RECORDING", { start() }, Modifier.fillMaxWidth(), outlined = true)
            TextButton(onClick = { confirmDiscard = true }) { Text("THROW THIS TRACK AWAY", color = Danger) }
            Text(
                "A saved track waits in the outbox until it reaches the Hub. The Hub keeps it only when you accept it there.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Btn("START A TRACK", { start() }, Modifier.fillMaxWidth())
            Text(
                "Records where you walk with the phone's GPS. It uses no internet. The track stays on the phone until you send it.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))
        Btn("BACK", onBack, Modifier.fillMaxWidth(), outlined = true)
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Throw this track away?") },
            text = { Text("The " + String.format(Locale.US, "%.2f", miles) + " miles recorded will be deleted from this phone.") },
            confirmButton = { TextButton(onClick = { TrackRecorder.discard(); confirmDiscard = false }) { Text("THROW AWAY") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("CANCEL") } },
        )
    }
}
