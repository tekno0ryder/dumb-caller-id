package com.ahmedalsinan.dumbcallerid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelegramTest {
    @Test
    fun acceptsBareUsername() = assertEquals("MyLookupBot", Telegram.parseBotName("MyLookupBot"))

    @Test
    fun stripsAtSignAndSpaces() = assertEquals("MyLookupBot", Telegram.parseBotName("  @MyLookupBot "))

    @Test
    fun acceptsPastedLinks() {
        assertEquals("MyLookupBot", Telegram.parseBotName("https://t.me/MyLookupBot"))
        assertEquals("MyLookupBot", Telegram.parseBotName("t.me/MyLookupBot/"))
        assertEquals("MyLookupBot", Telegram.parseBotName("https://t.me/MyLookupBot?start=abc"))
    }

    @Test
    fun emptyInputMeansDefaultBot() {
        assertEquals("", Telegram.parseBotName(""))
        assertEquals("", Telegram.parseBotName("   "))
    }

    @Test
    fun rejectsInvalidUsernames() {
        assertNull(Telegram.parseBotName("abc")) // too short
        assertNull(Telegram.parseBotName("a".repeat(33))) // too long
        assertNull(Telegram.parseBotName("my bot"))
        assertNull(Telegram.parseBotName("my-bot"))
        assertNull(Telegram.parseBotName("بوت_البحث"))
    }
}
