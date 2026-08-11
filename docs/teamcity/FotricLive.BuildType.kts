package _Self.buildTypes

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs

/**
 * Fotric.Live Android CI (TeamCity Kotlin DSL 2019.2)
 *
 * Prerequisites on build agent:
 * - JDK 11 x64
 * - Android SDK (ANDROID_SDK_ROOT; ANDROID_HOME optional, same path)
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
        branchFilter = "+:dev"
    }

    triggers {
        vcs {
            branchFilter = "+:dev"
        }
    }

    steps {
        script {
            name = "Check environment"
            workingDir = "src"
            scriptContent = """
                powershell -NoProfile -ExecutionPolicy Bypass -File "%teamcity.build.checkoutDir%\src\scripts\check-env.ps1"
            """.trimIndent()
        }

        gradle {
            name = "Assemble Release APK"
            tasks = "clean :app:assembleRelease"
            buildFile = "build.gradle"
            workingDir = "src"
            useGradleWrapper = true
            enableStacktrace = true
            gradleParams = "--no-daemon -Dorg.gradle.jvmargs=-Xmx4g"
        }
    }

    artifactRules = """
        src/app/build/outputs/apk/release/*.apk => apk/release
    """.trimIndent()
})
