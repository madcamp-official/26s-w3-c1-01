// 루트 빌드 스크립트 — 서브프로젝트에 플러그인 버전만 선언(적용은 각 모듈에서)
//
// 통합 시 A(AGP 9.3.0)와 D(AGP 8.5.2 + Kotlin 2.0.20 + Compose)가 충돌했고
// **A쪽으로 확정**했다. 근거:
//   - AGP 8.5.2는 Gradle 8.9를 요구하는데 이 회선은 136MB 배포판을 받지 못한다.
//     9.5.1은 이미 로컬 캐시에 있다.
//   - D의 Compose 사용처는 DebugActivity 한 파일뿐이었고 통합 진입점으로 대체되므로
//     Compose를 통째로 걷어냈다. 오버레이(OverlayView)는 원래부터 Canvas View다.
//
// JetBrains Kotlin 플러그인(org.jetbrains.kotlin.android)을 여기 추가하면 안 된다.
// AGP 9부터 Kotlin 지원이 내장이라 'kotlin' 확장을 AGP가 이미 등록하고,
// 같은 이름으로 또 등록하려다 "Cannot add extension with name 'kotlin'"으로 죽는다.
// Compose 컴파일러 플러그인도 같은 이유로 붙이지 않는다.
plugins {
    id("com.android.application") version "9.3.0" apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
}
