# Part B — 음성 인식 및 명령 해석 엔진

## 현재 역할

`VoiceRecognitionEngine`은 Android `SpeechRecognizer`를 감싸고, 인식 결과를 `CommandDictionary`로 정규화한다. B는 컨트롤러 상태를 모른다. 상태별 유효성 검증은 D의 `CommandGate`가 담당한다.

## 현재 명령어 사전

`CommandDictionary.definitions`가 음성 명령의 단일 관리 지점이다.

| commandId | 동의어 |
| --- | --- |
| TOUCH | 탭, 터치, 클릭 |
| BACK | 취소, 뒤로 |
| SCROLL_DOWN | 아래로, 내려, 아래 |
| SCROLL_UP | 위로, 올려, 위 |
| LOCK | 잠금, 끝, 멈춰 |
| UNLOCK | 해제 |
| NEXT | 다음, 오른쪽 |
| PREV | 이전, 왼쪽 |
| EXIT | 종료 |

`EXIT`는 앱 종료 전용 특수 명령이다. D의 `CommandId` enum에 넣지 않고 `VoiceCommandSourceAdapter`가 직접 처리한다.

## 제거된 명령

드래그, 일시정지/재개, 스크롤 강도 명령은 MVP에서 제거했다. 테스트는 제거된 문구가 더 이상 매칭되지 않는지도 검증한다.

## 검증

```powershell
java -jar .\gradle\wrapper\gradle-wrapper.jar testDebugUnitTest
```
