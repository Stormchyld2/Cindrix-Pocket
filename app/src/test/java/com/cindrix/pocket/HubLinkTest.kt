package com.cindrix.pocket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HubLinkTest {
    @Test
    fun readsAnInviteQr() {
        val code = "ab12cd34ef56ab12cd34ef56ab12cd34ef56ab12cd34ef56"
        assertEquals("http://192.168.43.1:8080" to code, HubLink.parseInvite("http://192.168.43.1:8080/join?c=$code"))
        assertEquals("http://192.168.43.1" to code, HubLink.parseInvite("  http://192.168.43.1/join?c=$code \n"))
    }

    @Test
    fun refusesAnythingThatIsNotAnInvite() {
        assertNull(HubLink.parseInvite("https://192.168.1.2/join?c=ab12cd34ef56"))
        assertNull(HubLink.parseInvite("http://evil.example/other?c=ab12cd34ef56"))
        assertNull(HubLink.parseInvite("http://192.168.1.2/join?c=zz"))
        assertNull(HubLink.parseInvite("0123456789012"))
    }

    @Test
    fun readsTheSignInFromTheCookie() {
        val token = "0123456789abcdef0123456789abcdef0123456789abcdef"
        assertEquals(token, HubLink.tokenFromSetCookie("cx=$token; Path=/; HttpOnly; SameSite=Lax; Max-Age=31536000"))
        assertNull(HubLink.tokenFromSetCookie("other=1; Path=/"))
        assertNull(HubLink.tokenFromSetCookie(null))
        assertEquals("01234567", HubLink.deviceId(token))
    }

    @Test
    fun readsTheHubsAnswer() {
        assertEquals(Hello("ok", listOf("a-track-1.txt", "a-track-2.txt")), HubLink.parseHello("{\"instruction\":\"ok\",\"accepted\":[\"a-track-1.txt\",\"a-track-2.txt\"]}"))
        assertEquals(Hello("erase", emptyList()), HubLink.parseHello("{\"instruction\":\"erase\",\"accepted\":[]}"))
        assertNull(HubLink.parseHello("<html>nope</html>"))
    }

    @Test
    fun fileNamesTravelSafely() {
        assertEquals("ab12cd34-track-1.txt", HubLink.encodeName("ab12cd34-track-1.txt"))
        assertEquals("a%20b", HubLink.encodeName("a b"))
    }

    @Test
    fun readsTheHubsStreamedAnswer() {
        assertEquals(AskEvent.Words(false, "Boil "), HubLink.parseAskEvent("data: {\"replace\":false,\"text\":\"Boil \"}"))
        assertEquals(AskEvent.Words(true, "Boil water"), HubLink.parseAskEvent("data: {\"replace\":true,\"text\":\"Boil water\"}"))
        assertEquals(AskEvent.Failed("Type a question first."), HubLink.parseAskEvent("data: {\"error\":\"Type a question first.\"}"))
        val done = HubLink.parseAskEvent("data: {\"done\":{\"kind\":\"answer\",\"text\":\"Boil it.\",\"sources\":[\"Field guide\"],\"links\":[],\"excerpts\":[\"one minute\"]}}")
        assertEquals(AskEvent.Done(HubAnswer("Boil it.", listOf("Field guide"), listOf("one minute"))), done)
        assertNull(HubLink.parseAskEvent(""))
        assertNull(HubLink.parseAskEvent(": keep-alive"))
        assertNull(HubLink.parseAskEvent("data: not json"))
    }

    @Test
    fun readsTheHubsPhoneMapList() {
        val json = """[{"name":"Camp-area.cmap","bytes":123456,"made":1763000000},{"name":"../evil.cmap","bytes":1,"made":1},{"name":"notes.txt","bytes":1,"made":1},{"name":".hidden.cmap","bytes":1,"made":1}]"""
        assertEquals(listOf(HubMap("Camp-area.cmap", 123456, 1763000000)), HubLink.parsePhoneMaps(json))
        assertEquals(emptyList<HubMap>(), HubLink.parsePhoneMaps("not json"))
    }
}
