package com.cindrix.pocket

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** What the Hub said to a hello: "ok", "erase" or "refused", plus the report names it has accepted. */
data class Hello(val instruction: String, val accepted: List<String>)

/** The Hub's finished answer to a question: the words, where they came from, and the passages found. */
data class HubAnswer(val text: String, val sources: List<String>, val excerpts: List<String>)

/** One line of the Hub's streamed answer. */
sealed class AskEvent {
    /** Words of the answer so far (`replace`) or more words to add (not `replace`). */
    data class Words(val replace: Boolean, val text: String) : AskEvent()
    data class Done(val answer: HubAnswer) : AskEvent()
    data class Failed(val message: String) : AskEvent()
}

/**
 * The phone's side of the Hub's trusted-device join (docs/34 and docs/45 in Cindrix-Ops).
 * The parts that read text are plain functions so they can be tested; the network calls are thin.
 */
object HubLink {
    /** A Hub invite QR holds http://<address>/join?c=<code>. Returns (address as http://host:port, code). */
    fun parseInvite(text: String): Pair<String, String>? {
        val t = text.trim()
        val m = Regex("^(http://[A-Za-z0-9.\\-]+(?::\\d{1,5})?)/join\\?c=([0-9a-fA-F]{8,128})$").find(t) ?: return null
        return m.groupValues[1] to m.groupValues[2]
    }

    /** The sign-in token from a `Set-Cookie: cx=<token>; ...` header, or null. */
    fun tokenFromSetCookie(header: String?): String? {
        val m = Regex("(?:^|[;,\\s])cx=([0-9a-fA-F]{16,128})").find(header ?: "") ?: return null
        return m.groupValues[1]
    }

    /** The device's own short code: the first eight characters of its sign-in, the same way the Hub makes it. */
    fun deviceId(token: String): String = token.take(8)

    fun parseHello(json: String): Hello? {
        val ins = Regex("\"instruction\"\\s*:\\s*\"([a-z]+)\"").find(json)?.groupValues?.get(1) ?: return null
        val list = Regex("\"accepted\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL).find(json)?.groupValues?.get(1) ?: ""
        val names = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(list).map { it.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\") }.toList()
        return Hello(ins, names)
    }

    /** Reads one `data: {...}` line of the Hub's answer stream; anything else (blank lines, comments) gives null. */
    fun parseAskEvent(line: String): AskEvent? {
        if (!line.startsWith("data:")) return null
        val o = try { org.json.JSONObject(line.removePrefix("data:").trim()) } catch (_: Exception) { return null }
        if (o.has("error")) return AskEvent.Failed(o.optString("error").ifBlank { "The Hub could not answer." })
        val d = o.optJSONObject("done")
        if (d != null) {
            fun list(key: String): List<String> = d.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
            return AskEvent.Done(HubAnswer(d.optString("text"), list("sources"), list("excerpts")))
        }
        if (o.has("text")) return AskEvent.Words(o.optBoolean("replace", false), o.optString("text"))
        return null
    }

    fun encodeName(name: String): String = URLEncoder.encode(name, "UTF-8").replace("+", "%20")

    // ---- network (call from a background thread) ----

    private fun open(url: String, method: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 8000
        c.readTimeout = 15000
        c.instanceFollowRedirects = false
        return c
    }

    /** Uses a one-use invite. Returns the sign-in token. */
    fun join(base: String, code: String): Result<String> = try {
        val c = open("$base/join?c=$code", "GET")
        val token = if (c.responseCode == 302) tokenFromSetCookie(c.getHeaderField("Set-Cookie")) else null
        c.disconnect()
        if (token != null) Result.success(token)
        else Result.failure(Exception("The invite was used up or has run out. Ask the Hub for a new one."))
    } catch (e: Exception) {
        Result.failure(Exception("Could not reach the Hub. Join the Hub's Wi-Fi first. (" + (e.message ?: "no answer") + ")"))
    }

    private fun post(base: String, token: String, path: String, body: String, fileName: String? = null): Pair<Int, String> {
        val c = open("$base$path", "POST")
        c.doOutput = true
        c.setRequestProperty("Cookie", "cx=$token")
        c.setRequestProperty("Content-Type", if (fileName == null) "application/json" else "text/plain; charset=utf-8")
        if (fileName != null) c.setRequestProperty("x-file-name", encodeName(fileName))
        c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        c.disconnect()
        return code to text
    }

    /** Says hello and learns what is waiting. A 403 with erase:true means the Hub has ordered this phone erased. */
    fun hello(base: String, token: String, label: String): Result<Hello> = try {
        val safe = label.replace("\\", "").replace("\"", "")
        val (code, text) = post(base, token, "/api/device_check_in", "{\"kind\":\"pocket\",\"label\":\"$safe\"}")
        val h = parseHello(text)
        when {
            h != null -> Result.success(h)
            code == 403 && text.contains("\"erase\":true") -> Result.success(Hello("erase", emptyList()))
            code == 401 || code == 403 -> Result.success(Hello("refused", emptyList()))
            else -> Result.failure(Exception("The Hub gave an answer this phone did not understand."))
        }
    } catch (e: Exception) {
        Result.failure(Exception("Could not reach the Hub. (" + (e.message ?: "no answer") + ")"))
    }

    fun upload(base: String, token: String, name: String, text: String): Result<Unit> = try {
        val (code, body) = post(base, token, "/api/pocket_upload", text, name)
        if (code in 200..299) Result.success(Unit)
        else Result.failure(Exception(Regex("\"error\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1) ?: "The Hub did not take the report."))
    } catch (e: Exception) {
        Result.failure(Exception("Could not reach the Hub. (" + (e.message ?: "no answer") + ")"))
    }

    fun erased(base: String, token: String): Result<Unit> = try {
        val (code, _) = post(base, token, "/api/device_erased", "{}")
        if (code in 200..299) Result.success(Unit) else Result.failure(Exception("The Hub did not confirm."))
    } catch (e: Exception) {
        Result.failure(Exception(e.message ?: "no answer"))
    }

    /** The Hub's shelf, keeping only the kinds Pocket can open (PDF, Markdown, plain text). */
    fun shelfList(base: String, token: String): Result<List<ShelfBook>> = try {
        val (code, text) = post(base, token, "/api/shelf_list", "{}")
        if (code !in 200..299) Result.failure(Exception("The Hub did not give its book list."))
        else {
            val arr = org.json.JSONArray(text)
            val out = ArrayList<ShelfBook>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val kind = o.optString("kind").lowercase()
                if (kind in Books.OPENABLE) out.add(ShelfBook(o.optString("id"), o.optString("title"), o.optString("author"), kind))
            }
            Result.success(out)
        }
    } catch (e: Exception) {
        Result.failure(Exception("Could not read the Hub's book list. (" + (e.message ?: "no answer") + ")"))
    }

    /** Downloads one book into `dest` (written to a temporary file first, so a broken download is never kept). */
    fun shelfRead(base: String, token: String, id: String, dest: java.io.File): Result<Unit> = try {
        val c = open("$base/api/shelf_read?id=" + URLEncoder.encode(id, "UTF-8"), "GET")
        c.readTimeout = 120000
        c.setRequestProperty("Cookie", "cx=$token")
        if (c.responseCode !in 200..299) {
            c.disconnect()
            Result.failure(Exception("The Hub could not give that book."))
        } else {
            val tmp = java.io.File(dest.parentFile, dest.name + ".part")
            c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            c.disconnect()
            if (tmp.renameTo(dest)) Result.success(Unit) else Result.failure(Exception("The book could not be saved."))
        }
    } catch (e: Exception) {
        Result.failure(Exception("The download stopped. (" + (e.message ?: "no answer") + ")"))
    }

    /**
     * Asks the Hub's assistant (the Hub searches its books and archives and writes the answer). `onText` gets the answer so
     * far each time it grows. Call from a background thread.
     */
    fun ask(base: String, token: String, question: String, history: List<String>, onText: (String) -> Unit): Result<HubAnswer> = try {
        val c = open("$base/api/ask_stream", "POST")
        c.readTimeout = 180000
        c.doOutput = true
        c.setRequestProperty("Cookie", "cx=$token")
        c.setRequestProperty("Content-Type", "application/json")
        val body = org.json.JSONObject().put("question", question).put("history", org.json.JSONArray(history.take(4))).toString()
        c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        if (code == 401 || code == 403) {
            c.disconnect()
            Result.failure(Exception("The Hub did not accept this phone. Join it again from the Hub screen."))
        } else if (code !in 200..299) {
            c.disconnect()
            Result.failure(Exception("The Hub could not answer right now."))
        } else {
            var text = ""
            var result: Result<HubAnswer>? = null
            c.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    when (val e = parseAskEvent(line)) {
                        is AskEvent.Words -> { text = if (e.replace) e.text else text + e.text; onText(text) }
                        is AskEvent.Done -> { result = Result.success(e.answer); break }
                        is AskEvent.Failed -> { result = Result.failure(Exception(e.message)); break }
                        null -> {}
                    }
                }
            }
            c.disconnect()
            result ?: if (text.isNotBlank()) Result.success(HubAnswer(text, emptyList(), emptyList())) else Result.failure(Exception("The Hub stopped before it finished answering."))
        }
    } catch (e: Exception) {
        Result.failure(Exception("Could not reach the Hub. (" + (e.message ?: "no answer") + ")"))
    }
}
