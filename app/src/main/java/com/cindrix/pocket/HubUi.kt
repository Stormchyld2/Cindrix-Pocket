package com.cindrix.pocket

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The Hub's Wi-Fi often has no internet, so Android would send the request over mobile data. Use the Wi-Fi for this app. */
@Suppress("DEPRECATION")
fun useWifiForHub(context: Context) {
    try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifi = cm.allNetworks.firstOrNull { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
        if (wifi != null) cm.bindProcessToNetwork(wifi)
    } catch (_: Exception) {
        // if it cannot be done, the normal route is tried
    }
}

@Composable
fun HubScreen(state: AppState, onScanQr: () -> Unit, onErased: () -> Unit, invite: String?, onInviteUsed: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var paste by remember { mutableStateOf("") }

    fun runHello() {
        val base = state.hubBase ?: return
        val token = state.hubToken ?: return
        scope.launch {
            busy = true
            useWifiForHub(context)
            val r = withContext(Dispatchers.IO) { HubLink.hello(base, token, state.source()) }
            busy = false
            val h = r.getOrNull()
            when {
                h == null -> status = r.exceptionOrNull()?.message ?: "Could not reach the Hub."
                h.instruction == "erase" -> {
                    // erase first, then tell the Hub it is done (the Hub waits for this)
                    state.wipeEverything()
                    withContext(Dispatchers.IO) { HubLink.erased(base, token) }
                    onErased()
                }
                h.instruction == "refused" -> {
                    state.unlink()
                    status = "The Hub no longer accepts this phone. Ask for a new invite to join again."
                }
                else -> status = "Connected. The Hub has accepted " + h.accepted.size + " report" + (if (h.accepted.size == 1) "" else "s") + " from this phone so far."
            }
        }
    }

    fun join(text: String) {
        val inv = HubLink.parseInvite(text)
        if (inv == null) {
            status = "That is not a Hub invite. Use the QR code under Settings, Sharing, Family devices on the Hub."
            return
        }
        scope.launch {
            busy = true
            useWifiForHub(context)
            val r = withContext(Dispatchers.IO) { HubLink.join(inv.first, inv.second) }
            busy = false
            val token = r.getOrNull()
            if (token == null) status = r.exceptionOrNull()?.message ?: "Could not join."
            else {
                state.link(inv.first, token)
                status = "Joined. This phone is now a trusted device of the Hub."
                runHello()
            }
        }
    }

    fun sendReports() {
        val base = state.hubBase ?: return
        val token = state.hubToken ?: return
        val code = state.deviceCode()
        scope.launch {
            busy = true
            useWifiForHub(context)
            var sent = 0
            var skipped = 0
            var problem: String? = null
            for (name in state.outboxStore.list().filter { it.endsWith(".txt") }) {
                val text = state.outboxStore.read(name) ?: continue
                if (!name.startsWith("$code-")) { skipped++; continue }
                val r = withContext(Dispatchers.IO) { HubLink.upload(base, token, name, text) }
                if (r.isSuccess) { state.delivered(name); sent++ } else { problem = r.exceptionOrNull()?.message; break }
            }
            busy = false
            status = (if (sent > 0) "Sent $sent report${if (sent == 1) "" else "s"} to the Hub. They wait there until the host accepts them. " else "") +
                (if (skipped > 0) "$skipped older file${if (skipped == 1) "" else "s"} made before joining stay in the outbox (they carry another code). " else "") +
                (problem ?: "") + (if (sent == 0 && skipped == 0 && problem == null) "Nothing was waiting to send." else "")
        }
    }

    LaunchedEffect(invite) {
        if (invite != null) { onInviteUsed(); join(invite) }
    }
    LaunchedEffect(state.linked) { if (state.linked && invite == null) runHello() }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Hub", fontFamily = HeadingFont, fontSize = 22.sp)
        if (!state.linked) {
            Text("Field view: Pocket works on its own and makes no requests. To reach the Hub, join its Wi-Fi, then scan the one-use invite QR code (Settings, Sharing, Family devices on the Hub).", color = Muted)
            Btn("SCAN THE HUB'S QR", onScanQr, Modifier.fillMaxWidth())
            OutlinedTextField(value = paste, onValueChange = { paste = it }, label = { Text("Or paste the invite link") }, modifier = Modifier.fillMaxWidth())
            Btn("JOIN WITH THIS LINK", { join(paste) }, Modifier.fillMaxWidth(), enabled = paste.isNotBlank() && !busy, outlined = true)
        } else {
            Text("Hub view: joined " + state.hubBase + " as device " + state.deviceCode() + ".", color = Blue)
            Text("Reports and tracks wait in the outbox until you send them. The Hub keeps them until the host accepts them.", color = Muted)
            Btn("SEND WAITING REPORTS (" + state.outbox.count { it.endsWith(".txt") } + ")", { sendReports() }, Modifier.fillMaxWidth(), enabled = !busy)
            Btn("CHECK THE HUB", { runHello() }, Modifier.fillMaxWidth(), enabled = !busy, outlined = true)
            Btn("LEAVE THE HUB", { state.unlink(); status = "Left the Hub. Pocket is back in field view." }, Modifier.fillMaxWidth(), enabled = !busy, outlined = true)
        }
        if (busy) Text("Working...", color = Muted)
        if (status.isNotBlank()) Text(status, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(PanelHigh).padding(12.dp))
        Spacer(Modifier.height(8.dp))
        Text("Joining uses the Hub's own Wi-Fi only. Nothing goes to the internet. If the Hub orders this phone erased, Pocket erases its cards progress, outbox and saved settings the next time it reaches the Hub.", fontSize = 12.sp, color = Muted)
    }
}

@Composable
fun HubScanScreen(onInvite: (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    var hint by remember { mutableStateOf("Point the camera at the invite QR code on the Hub") }
    if (!granted) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Joining the Hub needs the camera to read its QR code. Nothing is recorded.", textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Btn("ALLOW CAMERA", { launcher.launch(Manifest.permission.CAMERA) }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Btn("BACK", onBack, Modifier.fillMaxWidth(), outlined = true)
        }
        return
    }
    Box(Modifier.fillMaxSize()) {
        CameraView(torchOn = false, onCode = { code ->
            if (HubLink.parseInvite(code) != null) onInvite(code) else hint = "That QR is not a Hub invite. Try the one under Family devices."
        }, onTorchSupport = {})
        Box(Modifier.align(Alignment.Center).fillMaxWidth(0.7f).height(240.dp).border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)))
        Text(hint, color = Color.White, modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 12.dp, vertical = 8.dp))
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp)) { Btn("BACK", onBack, Modifier.fillMaxWidth()) }
    }
}
