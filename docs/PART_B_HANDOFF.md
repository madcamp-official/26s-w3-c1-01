# Part B — 음성 인식 및 명령 해석 엔진

`docs/기능명세서_partB.md` (FR-007) 구현체.

## 구조

```
app/src/main/java/com/madcamp/handsfree/
  voice/
    CommandDictionary.kt   # 17개 commandId + 동의어 SSOT, 연속 발화 분리 매칭
    VoiceCommandEvent.kt   # VoiceCommandEvent, VoiceEngineError, VoiceCommandListener
    VoiceRecognitionEngine.kt  # SpeechRecognizer 래퍼, 연속 청취 루프
  MainActivity.kt          # 데모 화면: 인식 시작/중지, 이벤트를 Logcat + 화면에 출력
```

## 다른 파트와의 연결점

- `VoiceCommandListener`를 D(상태 관리 모듈)가 구현해 `VoiceRecognitionEngine(context, listener)`로 주입하면 통합 완료.
- B는 상태(ACTIVE/PAUSED/LOCKED 등)를 전혀 참조하지 않는다 — 유효성 판단은 D의 몫.
- `CommandDictionary.definitions`가 commandId의 SSOT. 명령 추가/변경 시 이 파일만 수정하고 B/C/D 3팀에 공지.

## 로컬 실행 방법

이 샌드박스 환경에는 Android SDK/JDK/Gradle이 없어 여기서 빌드를 검증하지 못했습니다. Android Studio(Hedgehog 이상)에서 열면:

1. 프로젝트 루트를 Android Studio로 Open → Gradle Sync (JDK 17 필요)
2. 실기기 또는 마이크가 연결된 에뮬레이터에서 `app` 실행
3. "인식 시작" 버튼 → RECORD_AUDIO 권한 허용 → 사전에 등록된 명령을 발화하면 화면/Logcat(tag: `VoiceEngine[PartB]`)에 `VoiceCommandEvent` JSON이 출력됨

단위 테스트(`CommandDictionaryTest`, 순수 Kotlin·기기 불필요)는 `./gradlew test`로 실행합니다.

## 완료 기준 체크 (명세서 §7)

- [x] 사전의 모든 명령(17개 commandId, 동의어 포함) 매칭 — `CommandDictionaryTest`로 검증
- [x] 신뢰도 임계값(기본 0.6) 미달 발화는 이벤트 미발생 — `VoiceRecognitionEngine.handleResults`
- [x] 사전에 없는 발화는 무시(오작동 없음) — `onUnrecognizedSpeech`만 호출, 명령 이벤트 없음
- [x] 연속 발화가 개별 이벤트로 분리되어 방출 — `CommandDictionary.matchAll` + 발화 단위 재시작 루프
- [x] 마이크 권한 거부 시 에러 이벤트 정상 전달 — `VoiceEngineError.MicPermissionError`
- [ ] 실기기/에뮬레이터에서 실제 STT 동작 확인 (Android 툴체인이 있는 환경에서 검증 필요)

## D팀과 확인 필요 (명세서 §8)

- `confidenceThreshold` 기본값 0.6을 최종 수치로 확정할지 여부
- `NEXT`/`PREV`(발표 시나리오) commandId 채택 여부
