plugins {
    // Kotlin 플러그인을 따로 적용하지 않는다 — AGP 9에 내장돼 있다.
    // 추가하면 'kotlin' 확장 중복 등록으로 Sync가 실패한다.
    id("com.android.application")
}

android {
    namespace = "com.madcamp.handsfree"
    // 이 컴퓨터에 설치된 플랫폼이 android-36.1(API 36)이다. 35로 두면 SDK 플랫폼을
    // 새로 받아야 하는데, 이 회선은 대용량 전송이 끊긴다 — 있는 걸 쓴다.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.madcamp.handsfree"
        // MediaPipe tasks-vision 요구사항이 24, CameraX 안정 동작 고려해 26으로 올렸다
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }

    androidResources {
        // .task 모델은 압축하면 MediaPipe가 메모리 매핑을 못 해서 로딩에 실패한다
        noCompress += "task"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // CameraX — 전면 카메라 프레임 스트림
    val cameraX = "1.4.0"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")

    // MediaPipe — 얼굴 랜드마크 + 홍채. ML Kit을 안 쓴 이유는 홍채가 없어서다(CLAUDE.md 참고)
    implementation("com.google.mediapipe:tasks-vision:0.10.18")
}
