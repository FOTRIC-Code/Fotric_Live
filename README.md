# Live.Android

IRtek Live — a network video streaming app for infrared thermal cameras

## Directory layout

```
Live.Android/
├── src/                             # Android project source
│   ├── settings.gradle
│   ├── build.gradle
│   ├── gradle/
│   ├── repo/                            # Local Maven (com.irtek:netsdk AAR + POM)
│   ├── app/
│   │   ├── build.gradle
│   │   └── src/main/
│   │       ├── AndroidManifest.xml
│   │       └── java/com/irtek/live/
│   │           ├── LiveApp.kt           # Application (SDK initialization)
│   │           ├── MainActivity.kt      # Entry point
│   │           ├── data/model/          # Data models
│   │           ├── ui/
│   │           │   ├── theme/           # Theme
│   │           │   ├── connect/         # Connection screen
│   │           │   ├── device/          # Device screen
│   │           │   ├── settings/        # Settings (8 tabs)
│   │           │   └── components/      # Shared components
│   │           └── util/                # Utilities
│   ├── gradlew / gradlew.bat
│   └── local.properties
├── docs/                            # Documentation
└── README.md
```

## Tech stack

- Kotlin + Jetpack Compose + Material3
- IRtekNetSDK (infrared thermal camera network SDK)
- compileSdk 36 / minSdk 24 / targetSdk 36
- Gradle 8.13 / AGP 8.13.2 / Kotlin 2.2.0

## Build

Open the `src/` directory in Android Studio to build.
