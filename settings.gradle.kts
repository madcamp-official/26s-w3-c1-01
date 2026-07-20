pluginManagement {
    repositories {
        // 플러그인 jar 중 kotlin-gradle-plugin이 15MB라 이 회선에서 받다가 끊긴다.
        // 미리 curl로 받아 offline-repo에 넣어뒀고, 여기서 먼저 찾게 한다.
        maven {
            name = "offlineWorkaround"
            url = uri("${rootDir}/offline-repo")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 이 회선은 10MB쯤에서 연결이 리셋되는데 Gradle은 이어받기를 못 한다.
        // 그래서 10MB가 넘는 아티팩트(MediaPipe 17MB)만 curl로 미리 받아 여기 두고,
        // Gradle은 네트워크를 안 타고 로컬에서 가져가게 한다.
        //
        // 회선이 정상인 팀원은 이 폴더가 비어 있어도 아래 google()에서 그냥 받아진다 —
        // 없으면 넘어가는 구조라 지워도 빌드가 깨지지는 않는다.
        maven {
            name = "offlineWorkaround"
            url = uri("${rootDir}/offline-repo")
        }
        google()
        mavenCentral()
    }
}

// 표기를 바꾸지 말 것. IDE가 .idea/ 아래에 이 이름으로 모듈을 캐시해 두는데,
// 대소문자만 달라져도 "Can't find module entity for ...app"으로 Sync가 죽는다.
// (통합 때 B의 "HandsFreeController" 표기를 가져왔다가 실제로 이걸로 막혔다)
rootProject.name = "HandsfreeController"
include(":app")
