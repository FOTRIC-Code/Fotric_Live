package _Self.buildTypes

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script

/**
 * Fotric.Live Android CI (TeamCity Kotlin DSL 2019.2)
 *
 * Prerequisites on build agent:
 * - JDK 11 x64
 * - Android SDK (ANDROID_HOME / ANDROID_SDK_ROOT)
 *
 * IRtekNetSDK is consumed as a prebuilt AAR from src/repo (no NDK / no SDK source checkout).
 * Version comes from src/gradle.properties (IRTEK_NETSDK_VERSION), pushed by the SDK CI.
 *
 * Repo layout expected after checkout:
 *   <checkout>/src/gradlew
 *   <checkout>/src/repo/com/irtek/netsdk/<ver>/netsdk-<ver>.aar
 *   <checkout>/src/app/...
 */
object FotricLive : BuildType({
    name = "Fotric.Live"

    vcs {
        root(Http19216810253000IRtekLiveAndroidGit)
        cleanCheckout = true
    }

    steps {
        script {
            name = "Check environment"
            workingDir = "src"
            scriptContent = """
                echo ==== Java ====
                java -version
                echo ==== ANDROID_HOME ====
                echo %ANDROID_HOME%
                echo %ANDROID_SDK_ROOT%
                if not exist "gradlew.bat" (
                  echo ERROR: gradlew.bat not found. Working directory must be src
                  exit /b 1
                )
                for /f "usebackq tokens=1,* delims==" %%A in (`findstr /R /C:"^IRTEK_NETSDK_VERSION=" gradle.properties`) do set "SDK_VER=%%B"
                if not defined SDK_VER (
                  echo ERROR: IRTEK_NETSDK_VERSION missing in src/gradle.properties
                  exit /b 1
                )
                if not exist "repo\com\irtek\netsdk\%SDK_VER%\netsdk-%SDK_VER%.aar" (
                  echo ERROR: missing local Maven AAR src/repo/com/irtek/netsdk/%SDK_VER%/netsdk-%SDK_VER%.aar
                  exit /b 1
                )
                echo Environment OK
            """.trimIndent()
        }

        gradle {
            name = "Assemble Debug APK"
            tasks = "clean :app:assembleDebug"
            buildFile = "build.gradle"
            workingDir = "src"
            useGradleWrapper = true
            enableStacktrace = true
            gradleParams = "--no-daemon -Dorg.gradle.jvmargs=-Xmx4g"
        }
    }

    artifactRules = """
        src/app/build/outputs/apk/debug/*.apk => apk/debug
        src/app/build/outputs/apk/release/*.apk => apk/release
    """.trimIndent()
})
