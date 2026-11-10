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
}
