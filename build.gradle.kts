plugins {
    // 팀원과 동일한 AGP 9.3.0. Gradle 9.5.1과 짝이다.
    //
    // JetBrains Kotlin 플러그인(org.jetbrains.kotlin.android)을 여기 추가하면 안 된다.
    // AGP 9부터 Kotlin 지원이 내장이라 'kotlin' 확장을 AGP가 이미 등록하고,
    // 같은 이름으로 또 등록하려다 "Cannot add extension with name 'kotlin'"으로 죽는다.
    id("com.android.application") version "9.3.0" apply false
}
