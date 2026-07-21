package com.madcamp.handsfree.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandDictionaryTest {

    @Test
    fun `all synonyms map to their commandId`() {
        CommandDictionary.definitions.forEach { def ->
            def.synonyms.forEach { synonym ->
                assertEquals(
                    "synonym '$synonym' should map to ${def.commandId}",
                    def.commandId,
                    CommandDictionary.match(synonym),
                )
            }
        }
    }

    @Test
    fun `unknown speech does not match`() {
        assertNull(CommandDictionary.match("안녕하세요"))
        assertTrue(CommandDictionary.matchAll("오늘 날씨 어때").isEmpty())
    }

    @Test
    fun `repeated speech splits into separate events`() {
        val matches = CommandDictionary.matchAll("아래로 아래로")
        assertEquals(2, matches.size)
        assertEquals("SCROLL_DOWN", matches[0].commandId)
        assertEquals("SCROLL_DOWN", matches[1].commandId)
    }

    @Test
    fun `removed scroll strength commands no longer match`() {
        assertTrue(CommandDictionary.matchAll("조금 아래로").isEmpty())
        assertTrue(CommandDictionary.matchAll("조금 위로").isEmpty())
        assertTrue(CommandDictionary.matchAll("크게 아래로").isEmpty())
        assertTrue(CommandDictionary.matchAll("크게 위로").isEmpty())
    }

    @Test
    fun `multi word commands can be chained`() {
        val matches = CommandDictionary.matchAll("드래그 시작 놓아")
        assertEquals(2, matches.size)
        assertEquals("DRAG_START", matches[0].commandId)
        assertEquals("DRAG_END", matches[1].commandId)
    }

    @Test
    fun `13 commandIds are defined`() {
        assertEquals(13, CommandDictionary.definitions.map { it.commandId }.distinct().size)
    }
}
