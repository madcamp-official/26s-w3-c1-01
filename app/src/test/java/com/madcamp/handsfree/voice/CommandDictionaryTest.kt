package com.madcamp.handsfree.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandDictionaryTest {

    @Test
    fun `모든 commandId의 모든 동의어가 정확히 매칭된다`() {
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
    fun `사전에 없는 발화는 매칭되지 않는다`() {
        assertNull(CommandDictionary.match("안녕하세요"))
        assertTrue(CommandDictionary.matchAll("오늘 날씨 어때").isEmpty())
    }

    @Test
    fun `연속 발화는 개별 이벤트로 분리된다`() {
        val matches = CommandDictionary.matchAll("아래로 아래로")
        assertEquals(2, matches.size)
        assertEquals("SCROLL_DOWN", matches[0].commandId)
        assertEquals("SCROLL_DOWN", matches[1].commandId)
    }

    @Test
    fun `짧은 어구가 긴 어구에 흡수되지 않는다`() {
        val matches = CommandDictionary.matchAll("조금 아래로")
        assertEquals(1, matches.size)
        assertEquals("SCROLL_DOWN_SMALL", matches[0].commandId)
    }

    @Test
    fun `두 단어 명령과 한 단어 명령이 이어져도 각각 매칭된다`() {
        val matches = CommandDictionary.matchAll("드래그 시작 놓아")
        assertEquals(2, matches.size)
        assertEquals("DRAG_START", matches[0].commandId)
        assertEquals("DRAG_END", matches[1].commandId)
    }

    @Test
    fun `17개 commandId가 모두 정의되어 있다`() {
        assertEquals(17, CommandDictionary.definitions.map { it.commandId }.distinct().size)
    }
}
