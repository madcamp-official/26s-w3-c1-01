package com.madcamp.handsfree.voice

/**
 * 시스템 전체 음성 명령의 단일 진실 공급원(SSOT).
 * commandId와 실제 한국어 동의어는 여기에서만 관리한다 — 다른 팀(C, D)은 commandId 문자열만 참조한다.
 */
data class CommandDefinition(val commandId: String, val synonyms: List<String>)

data class CommandMatch(val commandId: String, val matchedText: String)

object CommandDictionary {

    val definitions: List<CommandDefinition> = listOf(
        CommandDefinition("TOUCH", listOf("터치", "클릭")),
        CommandDefinition("BACK", listOf("취소")),
        CommandDefinition("DRAG_START", listOf("잡아", "드래그 시작")),
        CommandDefinition("DRAG_END", listOf("놓아", "놔")),
        CommandDefinition("DRAG_CANCEL", listOf("드래그 취소")),
        CommandDefinition("SCROLL_DOWN", listOf("아래로", "내려")),
        CommandDefinition("SCROLL_UP", listOf("위로", "올려")),
        CommandDefinition("SCROLL_DOWN_SMALL", listOf("조금 아래로")),
        CommandDefinition("SCROLL_UP_SMALL", listOf("조금 위로")),
        CommandDefinition("SCROLL_DOWN_LARGE", listOf("크게 아래로")),
        CommandDefinition("SCROLL_UP_LARGE", listOf("크게 위로")),
        CommandDefinition("STOP", listOf("멈춰")),
        CommandDefinition("RESUME", listOf("다시 시작")),
        CommandDefinition("LOCK", listOf("잠금")),
        CommandDefinition("UNLOCK", listOf("해제")),
        CommandDefinition("NEXT", listOf("다음", "오른쪽")),
        CommandDefinition("PREV", listOf("이전", "왼쪽")),
    )

    private val phraseToCommandId: Map<String, String> =
        definitions.flatMap { def -> def.synonyms.map { it to def.commandId } }.toMap()

    private val maxPhraseWordCount: Int =
        definitions.flatMap { it.synonyms }.maxOf { it.trim().split(Regex("\\s+")).size }

    /** 정규화된 텍스트에서 가장 먼저 매칭되는 명령 하나만 필요할 때 사용. */
    fun match(text: String): String? = matchAll(text).firstOrNull()?.commandId

    /**
     * 발화 텍스트에서 사전에 등록된 명령을 순서대로 모두 찾는다.
     * "조금 아래로 조금 위로"처럼 한 문장에 여러 명령이 이어져도 개별 매치로 분리된다.
     * 각 위치에서 가장 긴 어구부터 매칭을 시도해 "조금 아래로"가 "아래로"로 잘못 쪼개지지 않도록 한다.
     */
    fun matchAll(text: String): List<CommandMatch> {
        val normalized = text.trim().replace(Regex("\\s+"), " ")
        if (normalized.isEmpty()) return emptyList()

        val words = normalized.split(" ")
        val results = mutableListOf<CommandMatch>()
        var i = 0
        while (i < words.size) {
            var window = minOf(maxPhraseWordCount, words.size - i)
            var matched = false
            while (window >= 1) {
                val candidate = words.subList(i, i + window).joinToString(" ")
                val commandId = phraseToCommandId[candidate]
                if (commandId != null) {
                    results.add(CommandMatch(commandId, candidate))
                    i += window
                    matched = true
                    break
                }
                window--
            }
            if (!matched) i++
        }
        return results
    }
}
