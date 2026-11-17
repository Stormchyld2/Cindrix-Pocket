package com.cindrix.pocket

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Maps. The phone maps (.cmap files) from Cindrix Ops (Maps > Send to phone) are kept on the phone and opened here, with
 * no Hub and no internet. The map itself is the standalone Cindrix map viewer's map code, run in a web view that
 * can reach nothing but the open map file.
 */
@Composable
fun MapsScreen(state: AppState, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val files = remember { MapFiles(context.filesDir) }
    var tick by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<Cmap?>(null) }
    val hubMaps = remember { androidx.compose.runtime.mutableStateListOf<HubMap>() }
    var copying by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(0L) }
    var copyTotal by remember { mutableStateOf(0L) }

    val current = open
    if (current != null) {
        MapView(current, onBack = { open = null })
        return
    }
    BackHandler(onBack = onBack)

    fun openFile(f: File) {
        scope.launch {
            busy = true
            val r = withContext(Dispatchers.IO) { try { Result.success(Cmap.open(f)) } catch (e: CmapException) { Result.failure<Cmap>(Exception(e.message)) } }
            busy = false
            r.onSuccess { open = it; status = "" }
            r.onFailure { status = it.message ?: "The map could not be opened." }
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            busy = true
            status = "Copying the map onto this phone…"
            scope.launch {
                val name = withContext(Dispatchers.IO) { displayName(context, uri) }
                val size = withContext(Dispatchers.IO) { fileSize(context, uri) }
                val tooBig = if (size > 0) MapFiles.spaceProblem(size, files.free()) else null
                if (tooBig != null) {
                    busy = false
                    status = tooBig
                    return@launch
                }
                val r = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                    if (input == null) Result.failure(Exception("That file could not be opened.")) else files.add(name, input)
                }
                busy = false
                tick++
                r.onSuccess { status = "Saved " + it.nameWithoutExtension + "."; openFile(it) }
                r.onFailure { status = it.message ?: "The map could not be added." }
            }
        }
    }

    @Suppress("UNUSED_EXPRESSION") tick
    val mine = files.list()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("BACK", fontFamily = HeadingFont) }
            Text("Maps", fontFamily = HeadingFont, fontSize = 18.sp)
        }
        Text(
            "Phone maps come from Cindrix Ops (Maps, then Send to phone). Joined to the Hub, copy them over its Wi-Fi; otherwise copy the .cmap file to this phone and add it here. Once on the phone a map works with no Hub and no signal.",
            color = Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp),
        )
        Btn(if (busy) "WORKING…" else "ADD A MAP FILE", { pick.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth(), enabled = !busy)
        LazyColumn(Modifier.weight(1f).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.linked) {
                item { Text("On the Hub", fontFamily = HeadingFont, color = Blue, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp)) }
                item {
                    Btn("LOAD THE HUB'S MAP LIST", {
                        val base = state.hubBase ?: return@Btn
                        val token = state.hubToken ?: return@Btn
                        scope.launch {
                            status = "Asking the Hub…"
                            useWifiForHub(context)
                            val r = withContext(Dispatchers.IO) { HubLink.phoneMapsList(base, token) }
                            r.onSuccess { hubMaps.clear(); hubMaps.addAll(it); status = if (it.isEmpty()) "The Hub has no phone maps yet. Make one in Cindrix Ops: Maps, then Send to phone." else it.size.toString() + " on the Hub." }
                            r.onFailure { status = it.message ?: "Could not load the list." }
                        }
                    }, Modifier.fillMaxWidth(), outlined = true, enabled = copying.isEmpty())
                }
                items(hubMaps.toList(), key = { "hub-" + it.name }) { m ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
                        Text(m.name.removeSuffix(".cmap"), fontWeight = FontWeight.Bold)
                        Text(MapFiles.sizeText(m.bytes) + if (files.has(m.name)) " · already on this phone" else "", fontSize = 12.sp, color = Muted)
                        if (copying == m.name) {
                            val pct = if (copyTotal > 0) (copied * 100 / copyTotal).toInt() else 0
                            Text("Copying… " + pct + "% (" + MapFiles.sizeText(copied) + " of " + MapFiles.sizeText(copyTotal) + ")", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                        } else {
                            TextButton(enabled = copying.isEmpty() && !busy, onClick = {
                                val base = state.hubBase ?: return@TextButton
                                val token = state.hubToken ?: return@TextButton
                                val part = files.partFor(m.name)
                                val needed = m.bytes - (if (part.isFile) part.length() else 0L)
                                val tooBig = MapFiles.spaceProblem(needed, files.free())
                                if (tooBig != null) { status = tooBig; return@TextButton }
                                copying = m.name; copied = 0L; copyTotal = m.bytes
                                status = "Copying " + m.name.removeSuffix(".cmap") + " from the Hub. Keep the phone on the Hub's Wi-Fi."
                                scope.launch {
                                    useWifiForHub(context)
                                    val r = withContext(Dispatchers.IO) { HubLink.phoneMapRead(base, token, m.name, part) { a, b -> copied = a; copyTotal = b } }
                                    val done = if (r.isSuccess) withContext(Dispatchers.IO) { files.adopt(part, m.name.removeSuffix(".cmap")) } else null
                                    copying = ""
                                    tick++
                                    when {
                                        done == null -> status = r.exceptionOrNull()?.message ?: "The copy stopped."
                                        done.isSuccess -> { status = "Saved " + m.name.removeSuffix(".cmap") + " on this phone."; openFile(done.getOrThrow()) }
                                        else -> status = done.exceptionOrNull()?.message ?: "The copy was not a real map."
                                    }
                                }
                            }) { Text(if (files.partFor(m.name).isFile) "CARRY ON COPYING" else "KEEP ON THIS PHONE") }
                        }
                    }
                }
            }
            item { Text("On this phone (" + mine.size + ")", fontFamily = HeadingFont, color = Blue, fontSize = 16.sp, modifier = Modifier.padding(top = if (state.linked) 12.dp else 0.dp)) }
            if (mine.isEmpty()) item { Text("No maps yet.", color = Muted) }
            items(mine, key = { it.name }) { f ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
                    Text(f.nameWithoutExtension, fontWeight = FontWeight.Bold)
                    Text(MapFiles.sizeText(f.length()), fontSize = 12.sp, color = Muted)
                    Row {
                        TextButton(enabled = !busy, onClick = { openFile(f) }) { Text("OPEN") }
                        TextButton(enabled = !busy, onClick = { files.remove(f); tick++ }) { Text("REMOVE", color = Danger) }
                    }
                }
            }
        }
        if (status.isNotBlank()) Text(status, color = Muted, modifier = Modifier.padding(top = 8.dp))
    }
}

private fun fileSize(context: Context, uri: Uri): Long = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
    } ?: -1L
} catch (_: Exception) {
    -1L
}

private fun displayName(context: Context, uri: Uri): String = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: ""
} catch (_: Exception) {
    ""
}

@SuppressLint("SetJavaScriptEnabled", "MissingPermission")
@Composable
private fun MapView(map: Cmap, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var web by remember { mutableStateOf<WebView?>(null) }
    var wantLocation by remember { mutableStateOf(false) }
    val main = remember { Handler(Looper.getMainLooper()) }
    val manager = remember { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }

    val listener = remember {
        object : LocationListener {
            override fun onLocationChanged(l: Location) {
                web?.evaluateJavascript("pocketMap.setMe(${l.latitude},${l.longitude},${if (l.hasAccuracy()) l.accuracy else 50f})", null)
            }
            @Deprecated("old API levels still call it") override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
            override fun onProviderEnabled(p: String) {}
            override fun onProviderDisabled(p: String) {}
        }
    }

    fun startLocation() {
        val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!ok) return
        try {
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 0f, listener, Looper.getMainLooper())
                manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { listener.onLocationChanged(it) }
            } else {
                web?.evaluateJavascript("pocketMap.note('Location is switched off on this phone. Turn it on in the phone settings.');pocketMap.clearMe()", null)
            }
        } catch (_: Exception) {
            web?.evaluateJavascript("pocketMap.note('Your position could not be found.');pocketMap.clearMe()", null)
        }
    }

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) startLocation()
        else web?.evaluateJavascript("pocketMap.note('Pocket needs the phone\\'s location to show where you are. Nothing is sent anywhere.');pocketMap.clearMe()", null)
    }

    LaunchedEffect(wantLocation) {
        if (wantLocation) {
            wantLocation = false
            val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (ok) startLocation() else askPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    // the track being walked is drawn over the map (the recorder keeps it; this only shows it)
    LaunchedEffect(web) {
        var last = -1
        while (true) {
            val pts = TrackRecorder.snapshot()
            if (web != null && pts.size != last) {
                last = pts.size
                val arr = pts.joinToString(",", "[", "]") { "[${it.lat},${it.lon}]" }
                web?.evaluateJavascript("pocketMap.setTrack($arr)", null)
            }
            delay(3000)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { manager.removeUpdates(listener) } catch (_: Exception) {}
            web?.destroy()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                // the page can read nothing on the phone and reach nothing on the network
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = false
                setBackgroundColor(0xFF1F2226.toInt())
                addJavascriptInterface(object {
                    @JavascriptInterface fun locate() { main.post { wantLocation = true } }
                    @JavascriptInterface fun locateOff() { main.post { try { manager.removeUpdates(listener) } catch (_: Exception) {} } }
                }, "Android")
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                        val host = request.url.host ?: ""
                        if (host != MapPage.HOST) {
                            return WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), java.io.ByteArrayInputStream(ByteArray(0)))
                        }
                        val a = MapPage.answer(request.url.encodedPath ?: "/", map) { name -> try { ctx.assets.open("map/$name") } catch (_: Exception) { null } }
                        return WebResourceResponse(
                            a.mime, "utf-8", a.status, if (a.status == 200) "OK" else "Not found",
                            mapOf("Access-Control-Allow-Origin" to "*", "Cache-Control" to "no-store"),
                            java.io.ByteArrayInputStream(a.bytes),
                        )
                    }
                }
                web = this
                loadUrl("https://" + MapPage.HOST + "/index.html")
            }
        },
    )
}
