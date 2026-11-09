package com.cindrix.pocket

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * The track being walked. Every fix is appended to a file on the phone the moment it arrives, so a dead battery or a
 * crash loses nothing: the unfinished track is found again the next time the app opens. Nothing leaves the phone
 * until the person saves the track to the outbox.
 */
object TrackRecorder {
    private var file: File? = null
    private var acc = TrackAccumulator()
    private val points = ArrayList<TrackPoint>()

    var recording by mutableStateOf(false)
        private set
    var stats by mutableStateOf(TrackStats())
        private set
    var lastEle by mutableStateOf<Double?>(null)
        private set
    /** True when a track is on the phone that has not been saved or thrown away. */
    var hasTrack by mutableStateOf(false)
        private set

    fun init(dir: File) {
        if (file != null) return
        file = File(dir, "track-current.txt")
        reload()
    }

    private fun reload() {
        points.clear()
        acc = TrackAccumulator()
        val f = file
        if (f != null && f.isFile) {
            f.readLines(Charsets.UTF_8).mapNotNull { TrackSheet.parseLine(it) }.forEach { points.add(it); acc.add(it) }
        }
        publish()
    }

    private fun publish() {
        stats = acc.stats()
        lastEle = points.lastOrNull()?.ele
        hasTrack = points.isNotEmpty()
    }

    /** Begin a new track. Refuses (returns false) while an unsaved one is still on the phone. */
    fun start(): Boolean {
        if (hasTrack && !recording) return false
        recording = true
        return true
    }

    /** Called with each fix by the service. */
    fun add(p: TrackPoint) {
        val f = file ?: return
        if (!recording) return
        val last = points.lastOrNull()
        if (last != null && p.t < last.t) return
        try {
            f.appendText(TrackSheet.line(p), Charsets.UTF_8)
        } catch (e: Exception) {
            return
        }
        points.add(p)
        acc.add(p)
        publish()
    }

    fun stop() {
        recording = false
    }

    fun snapshot(): List<TrackPoint> = points.toList()

    /** Throw the unsaved track away. */
    fun discard() {
        recording = false
        try { file?.delete() } catch (e: Exception) { /* nothing to do */ }
        points.clear()
        acc = TrackAccumulator()
        publish()
    }
}
