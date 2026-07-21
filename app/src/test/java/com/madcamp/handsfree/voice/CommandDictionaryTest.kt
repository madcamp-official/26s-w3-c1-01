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
    fun `lock synonyms include stop and end phrases`() {
        // "끝"/"멈춰"도 잠금으로 매핑된다 — 별도 STOP/일시정지(RESUME)는 폐기했다.
        listOf("잠금", "끝", "멈춰").forEach {
            assertEquals("LOCK", CommandDictionary.match(it))
        }
    }

    @Test
    fun `exit is its own command, not lock`() {
        // "종료"는 앱 종료 전용 명령이다. 잠금과 분리했다.
        assertEquals("EXIT", CommandDictionary.match("종료"))
    }

    @Test
    fun `removed drag and pause commands no longer match`() {
        // "드래그 취소"는 제외 — "취소"가 BACK(뒤로가기)으로 살아 있어 그 부분이 매칭된다.
        listOf("잡아", "놓아", "놔", "드래그 시작", "다시 시작").forEach {
            assertTrue("'$it' should no longer match", CommandDictionary.matchAll(it).isEmpty())
        }
    }

    @Test
    fun `9 commandIds are defined`() {
        assertEquals(9, CommandDictionary.definitions.map { it.commandId }.distinct().size)
    }
}
