# 공개 배포 전 보안 점검 — 팀 공유

> 2026-07-20 · `integration` 브랜치 · 커밋 `7925279`, `04da9ac`
> 담당: A파트

옵션 2는 **낯선 사람에게 APK를 설치시키고 접근성 권한까지 받는다.** 팀 내부에서만
쓸 때와 위험 수준이 다르다고 보고 전체 코드를 훑었다. 아래는 바꾼 것과
**각자 해야 할 일**이다. 판단 근거 전문은 [INTEGRATION.md](INTEGRATION.md) §7에 있다.

---

## 🔴 지금 당장 해야 하는 일 (Firebase 담당자)

### Firestore 보안 규칙을 교체해야 한다

**현재 콘솔 규칙이 테스트 모드 그대로다.**

```javascript
match /{document=**} { allow read, write: if request.time < timestamp.date(2026, 8, 19); }
```

이건 **만료 전까지 누구에게나 전체 읽기·쓰기를 허용한다.** `app/google-services.json`이
공개 저장소에 있어서 프로젝트 ID는 비밀이 아니다. 즉 **앱을 설치하지 않은 사람도
`telemetry_feedback`(사용자가 직접 쓴 자유 텍스트)까지 전부 읽고, 지우고, 조작할 수 있다.**

> `google-services.json`을 gitignore로 옮기는 건 대응이 아니다. Firebase 클라이언트
> 설정은 설계상 비밀이 아니고, **유일한 보호막이 보안 규칙이다.**

**작업 순서:**

1. 저장소 루트의 [`firestore.rules`](../firestore.rules) 전체 복사
2. Firebase 콘솔 → **Firestore Database** → **규칙** 탭
3. 기존 내용 전부 삭제 후 붙여넣기 → **게시**
4. 확인: **Rules Playground**에서 `telemetry_events`에 `get` 시뮬레이션 → **거부(Denied)**

**콘솔 데이터 뷰어는 그대로 보인다.** 관리자는 규칙을 우회하므로 팀이 통계를 보는 데는
지장이 없다. 앱(클라이언트)에서만 못 읽게 되는 것이다.

---

## ⚠️ 텔레메트리 담당자에게 — 코드가 바뀌었다

작업 중에 `Track telemetry users and overview totals`(`e63ded3`)가 올라와서
**충돌을 정리했다. 기능은 그대로 돌아가지만 기준이 바뀌었다.**

### 1. 사용자 집계 기준: `sessionId` → `installId`

`sessionId`를 고유 사용자 ID로 쓰고 있었는데(`"userId" to sessionId`),
**그 값은 실제로는 세션이 아니라 설치 식별자였다.** SharedPreferences에 한 번 만들고
앱을 지울 때까지 유지된다. 앱 화면이 사용자에게 "**익명** 진단 데이터"라고 고지하는데,
영구 식별자 + 기기 모델 + Android 버전이면 한 사람의 전 사용 이력이 하나로 묶인다.
익명이 아니라 가명(pseudonymous)이다.

**지표를 버리는 대신 이름과 고지를 사실에 맞췄다.** "공개 경로로 유입된 실제 사용자 수"는
옵션 2에서 가장 중요한 지표라 포기할 수 없다고 봤다.

| | 용도 | 수명 |
| --- | --- | --- |
| `installId` | **고유 사용자 집계** (`telemetry_users`, `totalUserCount`) | 설치 단위, 영구 |
| `sessionId` | 한 번의 사용 흐름(실행→보정→명령들) 묶기 | 앱 실행 단위, 저장 안 함 |

- `groupBy { it.sessionId }` → `groupBy { it.installId }`
- **저장 키는 `"session_id"` 그대로 승계했다.** 키를 바꾸면 기존 설치가 전부 신규로
  잡혀서 `totalUserCount`가 한 번 부푼다. **이 키를 바꾸지 말 것.**
- 화면 문구에서 "익명"을 빼고, 설치마다 무작위 식별자를 만들어 보낸다는 사실과
  앱을 지우면 사라진다는 점을 명시했다.

**`sessionId`로 사용자를 세면 앱을 켤 때마다 신규 사용자가 된다.** 새 집계를 추가할 때
어느 쪽을 쓰는지 확인할 것.

### 2. `telemetry_users`만 클라이언트 읽기를 연다

`upsertUsersAndOverview()`가 `transaction.get(userRef)`로 **"이 설치가 처음인가"를
읽는다.** 그래서 "읽기 전면 차단"을 그대로 적용하면 **트랜잭션이 실패해서 업로드
전체가 죽는다.**

`telemetry_users`에 한해 `get`(단건)만 열고 `list`(질의·열거)는 막았다. 문서 ID가
`installId`(UUID)라서 **자기 UUID를 아는 클라이언트만 자기 문서를 읽을 수 있고**
남의 것을 훑을 수는 없다. 이 구분을 안 하면 전체 사용자 목록이 그대로 노출된다.

### 3. 🔑 새 Firestore 컬렉션을 추가하면 `firestore.rules`에도 규칙을 넣어야 한다

규칙 맨 아래에 이게 있다:

```javascript
match /{document=**} { allow read, write: if false; }
```

**명시하지 않은 경로는 전부 막힌다.** 규칙을 빠뜨리면 그 기능만 조용히 실패하고,
로그에는 권한 에러만 남아서 원인을 찾기 어렵다. `firestore.rules`를 고친 뒤
**콘솔에 다시 게시하는 것까지가 한 세트다.**

---

## 📋 B파트 담당자에게 — 발화 로그

`VoiceCommandSourceAdapter.onUnrecognizedSpeech`가 **인식된 문장을 그대로 로그에
찍고 있었다.** 마이크가 상시 켜져 있는 앱이라 여기 들어오는 건 명령어가 아닌
**모든 소리**다 — 사용자의 혼잣말, 옆 사람 대화, TV 소리까지.

세 가지가 동시에 깨진다:

- NFR: "얼굴 이미지·음성 원본은 저장하지 않는다"
- 앱이 화면에서 하는 고지: "수집하지 않음: ... 음성 녹음"
- `isMinifyEnabled = false`라 **이 로그가 릴리스 APK에 그대로 들어간다**

**글자 수만 남기도록 고쳤다.** 디버깅에 필요한 "짧게 끊겼는지 / 길게 말했는데 못
알아들었는지"는 길이로 구분된다.

> **앞으로도 인식 결과 문자열을 로그에 넣지 말 것.** 사전에 매칭된 단어
> (`event.rawText` = `matchedText`)는 명령어 목록 안의 값이라 안전하다.

---

## 📋 C파트 담당자에게 — 접근성 서비스 설정

`accessibility_service_config.xml`에서 **`accessibilityEventTypes="typeAllMask"`를
아예 제거했다.**

> **속성을 비워 둔 것이 의도다. `typeNone`으로 채우지 말 것** — 이 속성은 flags라
> "없음"을 뜻하는 값이 없고, 쓰면 `'typeNone' is incompatible with attribute
> accessibilityEventTypes`로 **빌드가 깨진다.** (실제로 한 번 겪었다.)
> 생략하면 기본값이 0 = 아무 이벤트도 받지 않음이고, 그게 원하는 상태다.

`GestureAccessibilityService.onAccessibilityEvent`가 **비어 있는데**, 다른 앱의
`TYPE_VIEW_TEXT_CHANGED`(= 사용자가 타이핑하는 내용)까지 우리 프로세스로 받고 있었다.
`canRetrieveWindowContent`가 없어서 실제 유출은 아니었지만, **안 쓰는 데이터는 애초에
안 받는 게 맞고** 스토어 접근성 심사에서 반드시 걸리는 항목이다.

**제스처 주입(`dispatchGesture`)과 이벤트 구독은 별개 권한이라 기능 영향이 없다.**
다만 실기기에서 터치/스와이프가 여전히 되는지 한 번 확인해 달라.

---

## 그 외 변경

- **업로드 테스트 버튼**("로컬 업로드 테스트(Logcat)", "Firebase 업로드 테스트")을
  디버그 빌드에서만 노출하도록 바꿨다. 기능은 지우지 않았다 — 배포본 화면에
  "Logcat" 같은 말이 보이면 앱이 미완성으로 읽힌다.
- `app/build.gradle.kts`에 `buildConfig = true` 추가 (AGP 9는 기본으로 안 만든다).

---

## 고치지 않기로 한 것 — 릴리스 빌드의 로그

`isMinifyEnabled = false`라 모든 `Log` 호출이 배포본에 들어간다. 하지만 발화 유출을
막은 뒤 남는 것은 **좌표·각도·타이밍뿐이라 민감 정보가 아니다.**

R8을 켜면 MediaPipe/Firebase의 리플렉션 사용처에서 새 버그가 날 수 있고,
**공개 직전에 그 위험을 지는 것이 로그를 지워 얻는 이득보다 크다고 판단했다.**
반대 의견이 있으면 말해 달라 — 되돌릴 수 있는 결정이다.

---

## ✅ 점검했고 문제 없는 것

- `allowBackup="false"` — 캘리브레이션·텔레메트리가 클라우드 백업에 안 올라감
- 텔레메트리 기본값 `false`(옵트인) + 수집/미수집 항목을 화면에 고지
- **얼굴 이미지·오디오·화면 내용은 어디에도 저장·전송되지 않는다** (코드로 확인)
- `OverlayService`는 `exported="false"`, 접근성 서비스는 `BIND_ACCESSIBILITY_SERVICE`로 보호
- 네트워크는 Firestore SDK(HTTPS)뿐. 평문 통신 없음
- 서명 키(`*.jks`, `*.keystore`, `keystore.properties`)는 gitignore 처리됨

> ⚠️ **배포용 서명 키를 만들면 절대 커밋하지 말 것.** 유출되면 남이 우리 앱을
> 사칭한 업데이트를 만들 수 있고, 키는 교체가 불가능하다.

---

## 남은 확인 (실기기 1회)

1. 음성 명령 → 터치/스와이프가 여전히 동작하는가 (접근성 설정 변경 확인)
2. 텔레메트리 동의 → 피드백 저장 → 업로드 후 콘솔에 데이터가 들어오는가
   (**규칙이 너무 빡빡하면 여기서 실패한다** — 실패 시 `TelemetryFirebase` 태그 확인)
3. 앱 재설치 없이 재실행했을 때 `totalUserCount`가 늘지 않는가 (installId 승계 확인)
