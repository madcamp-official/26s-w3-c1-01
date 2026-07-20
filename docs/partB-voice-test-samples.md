# Part B 음성 명령 테스트 발화 샘플

`기능명세서_partB.md` §6(병렬 개발 전략)이 요구하는 "사전의 모든 commandId × 동의어 조합" 검증용 자료.
실기기/에뮬레이터에서 `MainActivity`로 아래 문장들을 실제로 말하며 `VoiceCommandEvent` 로그(Logcat tag: `VoiceEngine[PartB]`)가 기대한 대로 찍히는지 확인한다.

## 1. 전체 명령어 발화 샘플 (17개 commandId, 동의어 포함)

| # | 발화 | 기대 commandId |
| --- | --- | --- |
| 1 | 터치 | TOUCH |
| 2 | 클릭 | TOUCH |
| 3 | 취소 | BACK |
| 4 | 잡아 | DRAG_START |
| 5 | 드래그 시작 | DRAG_START |
| 6 | 놓아 | DRAG_END |
| 7 | 드래그 취소 | DRAG_CANCEL |
| 8 | 아래로 | SCROLL_DOWN |
| 9 | 위로 | SCROLL_UP |
| 10 | 조금 아래로 | SCROLL_DOWN_SMALL |
| 11 | 조금 위로 | SCROLL_UP_SMALL |
| 12 | 크게 아래로 | SCROLL_DOWN_LARGE |
| 13 | 크게 위로 | SCROLL_UP_LARGE |
| 14 | 멈춰 | STOP |
| 15 | 다시 시작 | RESUME |
| 16 | 잠금 | LOCK |
| 17 | 해제 | UNLOCK |
| 18 | 다음 | NEXT |
| 19 | 이전 | PREV |

완료 기준: 19개 발화 전부 해당 commandId로 정확히 매칭되고, JSON 로그의 `rawText`가 실제 발화와 일치해야 한다.

## 2. 연속 발화(분리 인식) 테스트

명세서 §5 "연속 발화는 개별 이벤트로 분리"를 실기기에서 검증한다. 한 번의 세션에서 쉬지 않고 이어 말한다.

| 발화 | 기대 이벤트 시퀀스 |
| --- | --- |
| "아래로 아래로" | SCROLL_DOWN → SCROLL_DOWN (2건) |
| "조금 아래로 조금 위로" | SCROLL_DOWN_SMALL → SCROLL_UP_SMALL (2건) |
| "잡아 놓아" | DRAG_START → DRAG_END (2건) |
| "터치 취소 터치" | TOUCH → BACK → TOUCH (3건) |

## 3. 오인식 방지(음성 사전에 없는 문장) — 이벤트가 발생하면 안 됨

| 발화 | 기대 결과 |
| --- | --- |
| "안녕하세요" | 이벤트 없음 (`onUnrecognizedSpeech`만 호출) |
| "오늘 날씨 어때" | 이벤트 없음 |
| "아래" (단독, "아래로" 아님) | 이벤트 없음 — 부분 문자열로 오매칭되지 않아야 함 |
| "위" (단독) | 이벤트 없음 |

## 4. 신뢰도/소음 환경 테스트 (실기기 전용, 자동화 불가)

- 조용한 환경에서 "터치" 발화 → 이벤트 발생 확인
- TV/음악 등 배경 소음이 있는 환경에서 동일 발화 → `confidence`가 낮게 나오면 이벤트가 억제되는지 확인 (기본 임계값 0.6, `VoiceRecognitionEngine.DEFAULT_CONFIDENCE_THRESHOLD`)
- 임계값 조정이 필요하다고 판단되면 D팀과 협의 후 생성자 파라미터로 전달

## 5. 권한 시나리오

- 최초 실행 시 "인식 시작" 버튼 → 마이크 권한 거부 → 화면/Logcat에 `MicPermissionError` 출력, 버튼이 "인식 시작"으로 복귀하는지 확인
- 설정에서 권한을 허용한 뒤 재시도 → 정상 인식 시작 확인

## 6. 실기기 테스트 절차 체크리스트

- [ ] 표 1의 19개 발화 전부 정확히 매칭
- [ ] 표 2의 연속 발화 4종 모두 개별 이벤트로 분리
- [ ] 표 3의 오인식 방지 4종 모두 이벤트 미발생
- [ ] 소음 환경에서 신뢰도 임계값 동작 확인
- [ ] 마이크 권한 거부/허용 플로우 확인

전부 통과하면 `app/README.md`의 DoD 마지막 항목("실기기/에뮬레이터에서 실제 STT 동작 확인")을 체크 완료로 갱신한다.
