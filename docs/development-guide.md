# Live.Android Development Environment Guide

## Overview

Live.Android is an Android app for managing infrared thermal devices. It is built with Kotlin and Jetpack Compose, and talks to devices through IRtekNetSDK (a JNI/C++ native SDK).

## Source paths

Paths below are relative to the Live.Android repository root.

| Path | Description |
|------|-------------|
| `src/` | App project (Gradle root) |
| `src/app/` | App module |
| `docs/` | Documentation |
| `../volga/Volga.IRtekNetSDK/` | IRtekNetSDK source repository |
| `../volga/Volga.IRtekNetSDK/android/` | SDK Android module (JNI + Kotlin) |
| `../volga/Volga.IRtekNetSDK/src/IRtekNetSDK/` | SDK core C++ source |
| `../volga/Volga.IRtekSDK_Android/IRtekNetSDK/demo/IRtekNetSDKDemo/` | Official SDK demo (for reference) |

## Dependencies

```
Live.Android (app)
├── com.irtek:netsdk:${IRTEK_NETSDK_VERSION} (local Maven: AAR + POM under src/repo)
│   ├── NativeSDK / NetSDKManager
│   └── libirtek_netsdk.so
│       └── ffmpeg-kit-min-gpl:5.1.LTS (transitive POM dependency)
├── Jetpack Compose (UI framework)
├── Room (local database)
└── Kotlin Coroutines (async)
```

### How the SDK is referenced

The app does not compile IRtekNetSDK from source. The version is set only in `src/gradle.properties`:

```
IRTEK_NETSDK_VERSION=1.0.1.61
```

```
src/repo/com/irtek/netsdk/<version>/netsdk-<version>.aar
src/repo/com/irtek/netsdk/<version>/netsdk-<version>.pom
```

`settings.gradle`:

```groovy
maven { url uri("${rootDir}/repo") }
```

`app/build.gradle`:

```groovy
implementation "com.irtek:netsdk:${findProperty('IRTEK_NETSDK_VERSION')}"
```

### Updating the SDK AAR

**TeamCity (recommended):** After `build_netsdk.bat` in the IRtekNetSDK build, add a `Push AAR to Live.Android` step (see `../volga/Volga.IRtekNetSDK/builder/teamcity-push-to-live.md` in the SDK repository). A successful build commits and pushes to Live's `dev` branch: it replaces `src/repo` and updates `IRTEK_NETSDK_VERSION`. Enable a VCS trigger on Live's `dev` branch to build the APK next.

Manual local publish:

```bat
gradlew :IRtekNetSDK:publishReleasePublicationToLocalBuildRepository
```

```powershell
Copy-Item -Recurse -Force `
  ..\volga\Volga.IRtekNetSDK\android\IRtekNetSDK\build\repo\* `
  src\repo\
```

Then set `IRTEK_NETSDK_VERSION` in `src/gradle.properties` to the Maven directory name (for example, `1.0.1.61`).

> Do not use the legacy `IRtekNetSDK-release.aar` (the OkHttp/Gson wrapper). Use the `com.irtek:netsdk` artifact produced by the current JNI module.

### Maven repositories

The SDK depends on `ffmpeg-kit`, which requires an extra Maven repository:

```groovy
// settings.gradle
maven { url 'https://artifactory.appodeal.com/appodeal-public' }
```

## Toolchain versions

| Tool | Version |
|------|---------|
| Android Studio | Meerkat or newer |
| Gradle | 8.13 |
| Android Gradle Plugin (AGP) | 8.13.2 |
| Kotlin | 2.2.0 |
| KSP | 2.2.0-2.0.2 |
| compileSdk / targetSdk | 36 |
| minSdk | 24 |
| JVM Target | 11 |
| NDK (SDK module) | 27.0.12077973 |
| CMake (SDK module) | 3.22.1 |

## Main dependency versions

| Dependency | Version |
|------------|---------|
| Compose BOM | 2024.09.00 |
| Material3 | Managed by the BOM |
| Room | 2.7.1 |
| Kotlin Coroutines | 1.7.3 |
| AndroidX Core KTX | 1.10.1 |
| Activity Compose | 1.8.0 |
| Lifecycle Runtime | 2.6.1 |

## Build and deploy

### Build a debug APK

From the repository root:

```bash
cd src
./gradlew assembleDebug
```

APK output: `src/app/build/outputs/apk/debug/app-debug.apk`

The first build of the SDK module runs CMake for the native libraries (arm64-v8a, armeabi-v7a, x86, x86_64) and takes about 2–3 minutes. Later incremental builds take about 3–5 seconds.

### Install on a device

```bash
# List connected devices
adb devices

# Install on a specific device (-s is required when more than one device is connected)
adb -s <device_serial> install -r src/app/build/outputs/apk/debug/app-debug.apk

# Launch the app
adb -s <device_serial> shell am start -n com.irtek.live/.MainActivity
```

### ADB wireless debugging

```bash
# Pair (first time)
adb pair <ip>:<pair_port>
# Enter the pairing code

# Connect
adb connect <ip>:<connect_port>
```

## SDK API notes

The SDK is a singleton (`NetSDKManager`). It keeps one connection handle and can connect to only one device at a time.

### Core API

```kotlin
// Initialize (call from Application.onCreate)
NetSDKManager.init()
NetSDKManager.enableLog(true)

// Log in to a device
val result = NetSDKManager.login(ip, port, username, password)

// Read device info → JSONObject
val info = NetSDKManager.getDeviceInfo()
info.data?.optString("name")         // Device name
info.data?.optString("model")        // Model
info.data?.optString("serial_no")    // Serial number
info.data?.optString("firmware_version") // Firmware version

// Thermal snapshot (saved to a local file)
NetSDKManager.getThermalCapture(mode = 0, localFilePath)

// Search for devices on the LAN → JSONArray
val devices = NetSDKManager.searchDevices(timeoutMs = 3000)

// Log out
NetSDKManager.logout()

// Tear down (call from Application.onTerminate)
NetSDKManager.cleanup()
```

### Multi-device operation

Because the SDK uses a single handle, operations on multiple devices must be serialized as login → operate → logout:

```kotlin
for (device in allDevices) {
    NetSDKManager.login(device.ip, device.port, user, pass)
    // Read info, capture snapshots, and so on
    NetSDKManager.logout()
}
```

## Project layout

```
src/app/src/main/java/com/irtek/live/
├── LiveApp.kt                  # Application; SDK initialization
├── MainActivity.kt             # Main activity; navigation and data coordination
├── data/
│   ├── AppDatabase.kt          # Room database
│   ├── dao/
│   │   ├── DeviceDao.kt        # Device table DAO
│   │   └── AlarmDao.kt         # Alarm table DAO
│   └── entity/
│       ├── DeviceEntity.kt     # Device entity
│       └── AlarmMessage.kt     # Alarm message entity
└── ui/
    ├── theme/                  # Theme, colors, spacing
    ├── devicelist/
    │   └── DeviceListScreen.kt # Device list
    ├── adddevice/
    │   ├── AddDeviceScreen.kt  # Add-device entry
    │   ├── ManualAddScreen.kt  # Manual add
    │   └── OnlineAddScreen.kt  # Add by online search
    ├── device/
    │   └── DeviceScreen.kt     # Device details
    └── components/
        └── SectionCard.kt      # Shared card component
```
