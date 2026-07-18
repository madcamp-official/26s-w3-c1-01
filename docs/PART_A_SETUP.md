# [A파트] 빌드 · 실행 · 검증

## 0. 필요한 것

| 항목 | 비고 |
| --- | --- |
| Android Studio | Gradle 8.7 / AGP 8.7.2 / Kotlin 2.0.21 을 다루는 최신 버전 |
| **실제 안드로이드 기기** | **에뮬레이터로는 검증 불가** — 전면 카메라로 실제 얼굴을 봐야 한다 |
| `face_landmarker.task` | `app/src/main/assets/`에 직접 배치 (같은 폴더 README.md 참고) |

> 이 코드는 **작성 환경에 Android SDK가 없어 컴파일된 적이 없다.**
> 첫 빌드에서 오류가 날 수 있고, 그건 정상적인 예상 범위다.

## 1. 첫 실행 절차

1. Android Studio로 저장소 루트를 연다 (Gradle wrapper는 Android Studio가 생성한다)
2. `app/src/main/assets/face_landmarker.task` 배치 확인
3. 실기기 연결 후 `app` 실행
4. 카메라 권한 허용
5. 검은 화면에 초록 점이 뜨고, 고개를 돌리면 따라오면 성공

## 2. 검증 항목 (A 명세 §6 DoD)

| # | 항목 | 확인 방법 |
| --- | --- | --- |
| 1 | `PointerFrame` 15fps 이상 | 화면 상단 `fps=` 표시가 15 이상 |
| 2 | 얼굴 미검출 시 `faceDetected:false` | 카메라를 손으로 가리면 점이 **회색**으로 바뀌고 그 자리에 멈춤 |
| 3 | 감도/스무딩 3단계 체감 차이 | 하단 버튼으로 순환하며 비교 |
| 4 | 범위 밖 각도에서 클램핑 | 고개를 크게 돌려도 점이 화면 밖으로 안 나감 |
| 5 | `RawFaceOrientation` 별도 방출 | 상단 둘째 줄 `yaw= pitch= eye=` 갱신 확인 |

## 3. 처음 켰을 때 십중팔구 마주칠 것

### 포인터가 반대로 움직인다

**가장 가능성 높은 첫 증상이다.** 전면 카메라 미러링과 기기별 센서 방향 때문에
부호가 뒤집힐 수 있다. 고칠 곳은 한 군데다:

`app/src/main/java/com/madcamp/handsfree/tracking/HeadPose.kt`

```kotlin
const val YAW_SIGN = -1f    // 좌우가 반대면 부호를 뒤집는다
const val PITCH_SIGN = -1f  // 상하가 반대면 부호를 뒤집는다
```

부호를 매핑 로직 곳곳에 흩뿌리지 않고 여기 모아둔 이유가 이것이다.

### 포인터가 화면 끝까지 안 간다 / 조금만 움직여도 끝에 붙는다

캘리브레이션 범위가 실제 사용 자세와 안 맞는 것이다. 지금은 D의 캘리브레이션이
없어서 더미 값을 쓴다:

`app/src/main/java/com/madcamp/handsfree/debug/MockCalibration.kt`
→ `faceRangeYawMin/Max`(기본 ±25°), `faceRangePitchMin/Max`(기본 ±18°)

끝까지 안 가면 범위를 좁히고, 너무 쉽게 끝에 붙으면 넓힌다.

### 점이 안 뜨고 "오류: MODEL_LOAD_FAILED"

`assets/face_landmarker.task`가 없다. assets/README.md 참고.

### fps가 15 미만

`FaceTracker`는 이미 최신 프레임만 처리하도록(`STRATEGY_KEEP_ONLY_LATEST`) 돼 있다.
그래도 낮으면 저사양 기기이므로 카메라 해상도 하향을 검토한다
(`ImageAnalysis.Builder().setResolutionSelector(...)`).

## 4. 통합 시 버릴 것

- `debug/` 패키지 전체 (`TrackerDebugActivity`, `PointerOverlayView`, `MockCalibration`)
- 오버레이는 D 담당이다. 여기 것은 A 검증용이지 D의 것을 대신 만든 게 아니다.
- 프로파일은 D의 캘리브레이션 결과가 `FaceTracker.updateProfile()`로 들어온다.

## 5. C/D가 A를 붙이는 법

```kotlin
val tracker = FaceTracker(context)
tracker.start(lifecycleOwner)

// D: 캘리브레이션 완료 후
tracker.updateProfile(profile)

// C, D: 좌표 구독
lifecycleScope.launch {
    tracker.pointerFrames.collect { frame -> /* ... */ }
}

// D: 캘리브레이션 중 원시값 구독
lifecycleScope.launch {
    tracker.rawOrientations.collect { raw -> /* 9개 기준점 수집 */ }
}
```

`pointerFrames`는 `SharedFlow(replay=1, DROP_OLDEST)`다. 소비 측이 느려도
트래킹이 밀리지 않고, 대신 **오래된 좌표는 버려진다.** 포인터 좌표는 최신값만
의미가 있으므로 의도한 동작이다.
