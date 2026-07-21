# [A파트] 핸드오프 — 얼굴/시선 추적 및 포인터 매핑 엔진

> 담당 FR: FR-001 · 작성 2026-07-18 · **실기기(갤럭시 S7, Android 8.0)에서 동작 확인 완료**
>
> 읽는 순서: 이 문서 → [PART_A_OPEN_ISSUES.md](PART_A_OPEN_ISSUES.md)(합의 필요 항목) →
> [PART_A_SETUP.md](PART_A_SETUP.md)(빌드/실행)

---

## 1. 한 줄 요약

**카메라를 보고 화면 좌표(0~1)를 15fps 안팎으로 계속 뱉는 모듈.**
컨트롤러 상태(ACTIVE/LOCKED)는 **모른다.** 상태 판단은 전부 D 몫이다.

## 2. C·D가 붙이는 법 (이것만 알면 됨)

```kotlin
val tracker = FaceTracker(context)
tracker.start(lifecycleOwner)          // 카메라 권한은 호출 전에 받아둘 것

// D: 캘리브레이션 끝나면 프로파일 주입 (재보정 시 다시 호출하면 즉시 반영)
tracker.updateProfile(profile)

// C, D: 포인터 좌표 구독
lifecycleScope.launch {
    tracker.pointerFrames.collect { frame -> /* frame.x, frame.y (0~1) */ }
}

// D: 캘리브레이션 중 원시 방향값 구독 (9개 기준점 수집용)
lifecycleScope.launch {
    tracker.rawOrientations.collect { raw -> /* raw.yaw, raw.pitch */ }
}

// D: 권한/모델 오류
lifecycleScope.launch {
    tracker.errors.collect { error -> /* CAMERA_PERMISSION_DENIED 등 */ }
}

tracker.stop()                          // 화면 종료 시 반드시
```

**D가 추가로 해줘야 하는 것 하나:** 기기 회전이 바뀌면 알려줘야 한다.

```kotlin
tracker.displayRotationDegrees = 0 / 90 / 180 / 270
```

회전 보정 자체는 A가 흡수하므로, **C와 D는 가로/세로를 신경 쓸 필요가 없다.**
`x`, `y`는 항상 "현재 회전 상태의 전체 화면" 기준 0~1이다.

## 3. 계약 (구현된 실제 모습)

명세서 §3 스키마 그대로다. 코드는
[contract/Contracts.kt](../app/src/main/java/com/madcamp/handsfree/contract/Contracts.kt).

| 방향 | 타입 | 비고 |
| --- | --- | --- |
| A → C, D | `PointerFrame` | `SharedFlow(replay=1, DROP_OLDEST)` |
| A → D | `RawFaceOrientation` | 프로파일 없어도 방출 |
| A → D | `TrackerError` | 명세서에 스키마가 없어 A가 정함 |
| D → A | `CalibrationProfile` | `updateProfile()`로 주입 |

### 명세서와 다르게 구현한 것 — **이것만은 꼭 읽을 것**

| # | 명세서 | 실제 구현 | 이유 |
| --- | --- | --- | --- |
| 1 | 프로파일 없으면 좌표 `null` | **null 안 보냄.** `faceDetected=false` + `(0.5, 0.5)` | C에 null 분기가 없어서 보내면 터진다 |
| 2 | 저조도 시 `confidence`를 낮춤 | `confidence`는 검출 신뢰도만. **`lowLight` 필드 추가** | 조도와 검출 품질은 다른 신호다 |
| 3 | `referencePoints[9]` 제공 | **쓰지 않음.** min/max 선형 정규화만 | 9점 보간은 MVP 비용에 안 맞음 |
| 4 | 파라미터 4개 | **2개만 동작**(감도·스무딩) | 나머지 2개는 프로파일에 전달 필드가 없음 |

`confidence`는 **현재 항상 1.0**이다. MediaPipe가 얼굴별 검출 점수를 따로 주지
않아서, 검출됐다는 사실 자체가 임계값을 넘었다는 뜻이 된다.
**D가 confidence 임계값으로 UI를 분기할 계획이면 이 값은 쓸 수 없다** — 대신
`lowLight`를 쓰거나 정책을 다시 정해야 한다.

## 4. 실기기 검증 결과 (갤럭시 S7 / Android 8.0 / Exynos 8890)

| DoD 항목 | 결과 |
| --- | --- |
| `PointerFrame` 15fps 이상 | **13~16fps** — 경계선. 아래 참고 |
| 얼굴 미검출 시 `faceDetected:false` + 좌표 유지 | ✅ |
| 감도/스무딩 3단계 체감 차이 | ✅ |
| 범위 밖 각도 클램핑 | ✅ |
| `RawFaceOrientation` 별도 방출 | ✅ |
| 고개 방향 ↔ 포인터 방향 일치 | ✅ (좌우/상하 모두) |

**fps 13~16은 2016년 기기의 한계에 가깝다.** 연산의 대부분이 신경망 자체라
코드 최적화로 크게 못 올린다. **더 최신 기기에서는 문제없을 것으로 보지만 확인된
바 없다** — 통합 담당자가 자기 기기에서 fps를 먼저 확인하는 게 좋다.

## 5. 시선 추적에 대한 정직한 한계 — 팀이 알아야 함

**"눈만 움직여 화면 전반을 탐색하는" 느낌은 나지 않는다.** 고개가 주 입력이고
눈은 미세 조정이다. 이건 버그가 아니라 명세서대로 만든 결과다:

- 기획안 4.1 *"정밀한 아이트래킹보다 안정적인 조작감을 우선"*
- FR-001 *"시선 보조 가중치"* 기본값 **낮음**
- 기능명세서 1.2 *"정밀 아이트래킹"* → **Phase 2 제외 범위**

**기술적 한계도 실재한다.** 현재 분석 해상도에서 홍채는 가로 10~15픽셀이다.
랜드마크가 1픽셀 흔들리면 화면에서 수십 픽셀이 튄다. 상용 아이트래커가 적외선
조명을 쓰는 이유다. **시선을 주 입력으로 바꾸려면 해상도를 올리고(=fps 하락)
캘리브레이션에서 시선 범위를 따로 재야 하며, 이는 명세서 변경 사항이다.**

기대치가 다르면 데모/발표에서 "이거 아이트래킹 아니네"가 나올 수 있으니 **팀이
미리 같은 인식을 갖고 있어야 한다.**

## 6. 실기기에서 잡은 버그 (같은 실수 반복 방지용)

| 증상 | 원인 |
| --- | --- |
| 깜빡일 때마다 포인터가 튐 | 세로 시선을 **눈 높이로 나눴는데** 감으면 높이가 0에 수렴 → 값 폭발. 지금은 눈 종횡비로 감김을 판정하고 직전 값 유지 |
| 눈 굴리는 방향과 포인터가 반대 | 전면 카메라 좌우 반전. 머리 방향은 뒤집었는데 **홍채 좌표만 안 뒤집음** |
| 눈을 굴려도 반응 없음 | 가중치 0.2가 너무 작았음 + smoothing이 작은 변화를 떨림으로 보고 깎아냄 |

## 7. 튜닝 상수 위치

기기나 사용 자세에 따라 조정이 필요하면 **여기만 보면 된다.**

| 무엇 | 파일 | 상수 |
| --- | --- | --- |
| 좌우/상하 부호 (머리) | `tracking/HeadPose.kt` | `YAW_SIGN`, `PITCH_SIGN` |
| 좌우 부호 (시선) | `tracking/EyeOffset.kt` | `GAZE_X_SIGN` |
| 눈 감김 판정 | `tracking/EyeOffset.kt` | `OPEN_EYE_RATIO` (0.22) |
| 시선 보조 세기 | `tracking/PointerMapper.kt` | `DEFAULT_GAZE_ASSIST_WEIGHT` (0.5) |
| 감도 3단계 배율 | `tracking/PointerMapper.kt` | `gainFor()` |
| 스무딩 3단계 강도 | `tracking/Smoother.kt` | `alphaFor()`, `ADAPT_GAIN` |
| 저조도 임계 | `tracking/FaceTracker.kt` | `LOW_LIGHT_LUMA` |

**부호를 매핑 로직 안에 흩뿌리지 말 것.** 한곳에 모아둔 덕에 "반대로 움직인다"를
한 줄로 고쳤다.

## 8. 통합할 때 지울 것

- **`debug/` 패키지 전체** — `TrackerDebugActivity`, `PointerOverlayView`, `MockCalibration`
- `AndroidManifest.xml`의 `.debug.TrackerDebugActivity` 선언 (LAUNCHER 인텐트 포함)
- `FaceTracker.lastLandmarkCount`, `FaceTracker.gazeAssistWeight` (디버그 전용 통로)

**오버레이는 D 담당이다.** `PointerOverlayView`는 A의 DoD("감도 3단계가 체감상
구분됨")를 눈으로 확인하려고 만든 검증 도구지, D의 오버레이를 대신 만든 게 아니다.

`MockCalibration`이 쓰던 더미 범위(yaw ±25°, pitch ±18°)는 **D의 캘리브레이션이
산출할 값의 참고치로는 쓸 만하다.** 실기기에서 이 범위로 화면 끝까지 무리 없이
닿았다.

## 9. 빌드 환경 주의 (다른 파트와 합칠 때)

이 프로젝트는 **AGP 9.3.0 + Gradle 9.5.1**이다. 팀원 환경(9.3.0 / 9.5.0)과 맞춘 것이다.

- **JetBrains Kotlin 플러그인(`org.jetbrains.kotlin.android`)을 추가하면 Sync가 죽는다.**
  AGP 9는 Kotlin이 내장이라 `kotlin` 확장이 중복 등록된다. 실제로 이걸로 한 번 막혔다.
- `compileSdk = 36` — 설치돼 있던 플랫폼에 맞춘 값이다.
- **`app/src/main/assets/face_landmarker.task`(3.7MB)는 저장소에 없다**(gitignore).
  각자 받아야 하고, 없으면 실행 즉시 `MODEL_LOAD_FAILED`가 뜬다.
  링크는 `app/src/main/assets/README.md`.
- **`offline-repo/`는 네트워크 우회용이다.** 작업 회선이 10MB 넘는 다운로드를
  끊어서, 큰 아티팩트만 미리 받아 로컬 저장소로 물려뒀다. 회선이 정상인 환경에서는
  없어도 되며, `settings.gradle.kts`에서 이 저장소를 지워도 빌드가 깨지지 않는다.

## 10. A가 안 한 것 (경계 확인용)

- 상태 판단 → **D**
- 캘리브레이션 플로우 진행/저장 → **D** (A는 `RawFaceOrientation`만 제공)
- 음성 인식 → **B**
- OS 터치 이벤트 주입 → **C**
- 화면 구역 이동(N×N) → **미구현.** 정의 자체가 없어 잘라냈다 (OPEN_ISSUES #2)
