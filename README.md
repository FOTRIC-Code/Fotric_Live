# Live.Android

IRtek Live - 红外热像仪网络视频流应用

## 目录结构

```
Live.Android/
├── src/                             # Android 项目源码
│   ├── settings.gradle
│   ├── build.gradle
│   ├── gradle/
│   ├── app/
│   │   ├── libs/
│   │   │   └── IRtekNetSDK-release.aar
│   │   ├── build.gradle
│   │   └── src/main/
│   │       ├── AndroidManifest.xml
│   │       └── java/com/irtek/live/
│   │           ├── LiveApp.kt           # Application (SDK 初始化)
│   │           ├── MainActivity.kt      # 入口
│   │           ├── data/model/          # 数据模型
│   │           ├── ui/
│   │           │   ├── theme/           # 主题
│   │           │   ├── connect/         # 连接页面
│   │           │   ├── device/          # 设备页面
│   │           │   ├── settings/        # 设置 (8个Tab)
│   │           │   └── components/      # 通用组件
│   │           └── util/                # 工具类
│   ├── gradlew / gradlew.bat
│   └── local.properties
├── docs/                            # 文档
└── README.md
```

## 技术栈

- Kotlin + Jetpack Compose + Material3
- IRtekNetSDK (红外热像仪网络SDK)
- compileSdk 36 / minSdk 24 / targetSdk 36
- Gradle 8.13 / AGP 8.13.2 / Kotlin 2.2.0

## 构建

用 Android Studio 打开 `src/` 目录即可构建。
