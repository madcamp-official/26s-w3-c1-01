plugins {
    // Kotlin 플러그인을 따로 적용하지 않는다 — AGP 9에 내장돼 있다.
    // 추가하면 'kotlin' 확장 중복 등록으로 Sync가 실패한다.
    id("com.android.application")
    id("com.google.gms.google-services")
}

android {
    // 통합 앱의 단일 namespace/applicationId.
    //
    // 소스 패키지는 파트별로 그대로 뒀다(com.madcamp.handsfree.tracking/.voice,
    // com.mobileconductor.*, com.example.hands_free_controller.*).
    // Kotlin에서 패키지명과 namespace는 무관해 공존에 문제가 없고, 리네임하면
    // 컴파일 에러만 수백 개 나고 얻는 게 없다.
    namespace = "com.madcamp.handsfree"
    // 이 컴퓨터에 설치된 플랫폼이 android-36.1(API 36)이다.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.madcamp.handsfree"
        // MediaPipe tasks-vision 요구사항이 24, CameraX와 오버레이
        // (TYPE_APPLICATION_OVERLAY) 안정 동작을 고려해 26으로 올렸다
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        // AGP 9는 buildConfig를 기본으로 만들지 않는다. 배포본에서 개발용 UI를
        // 숨기려면 BuildConfig.DEBUG가 필요해서 켠다
        buildConfig = true
    }

    androidResources {
        // .task 모델은 압축하면 MediaPipe가 메모리 매핑을 못 해서 로딩에 실패한다
        noCompress += "task"
    }

    testOptions {
        // D의 유닛 테스트가 android.* 스텁을 호출해도 예외 대신 기본값을 받게 한다
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    // D의 OverlayService가 LifecycleService를 상속한다
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation(platform("com.google.firebase:firebase-bom:34.16.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // CameraX — 전면 카메라 프레임 스트림 (A)
    val cameraX = "1.4.0"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")

    // MediaPipe — 얼굴 랜드마크 + 홍채. ML Kit을 안 쓴 이유는 홍채가 없어서다(CLAUDE.md 참고)
    implementation("com.google.mediapipe:tasks-vision:0.10.18")

    // 단위 테스트 (D의 게이트/상태머신 전수 검증 + B의 명령어 사전)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
