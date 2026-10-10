package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TradeRepublicLinkPolicyTest {
    @Test
    fun acceptsOfficialHttpsHosts() {
        assertEquals(
            "https://traderepublic.com/de-de",
            TradeRepublicLinkPolicy.sanitize(" https://traderepublic.com/de-de ")
        )
        assertEquals(
            "https://app.traderepublic.com/stocks/DE0008404005",
            TradeRepublicLinkPolicy.sanitize("https://app.traderepublic.com/stocks/DE0008404005")
        )
    }

    @Test
    fun rejectsHttpAndLookalikeHosts() {
        assertNull(TradeRepublicLinkPolicy.sanitize("http://traderepublic.com/de-de"))
        assertNull(TradeRepublicLinkPolicy.sanitize("https://traderepublic.com.evil.example/login"))
        assertNull(TradeRepublicLinkPolicy.sanitize("https://evil.example/?next=https://traderepublic.com"))
        assertNull(TradeRepublicLinkPolicy.sanitize("javascript:alert(1)"))
    }

    @Test
    fun blankInputStaysBlank() {
        assertEquals("", TradeRepublicLinkPolicy.sanitizeOrBlank("   "))
    }
}
