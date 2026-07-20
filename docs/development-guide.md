# Live.Android 开发环境指南

## 项目概览

Live.Android 是一款红外热像设备管理 Android 应用，使用 Kotlin + Jetpack Compose 构建，通过 IRtekNetSDK（JNI/C++ 原生 SDK）与设备通信。

## 源码路径

| 目录 | 说明 |
|------|------|
| `F:\Live.Android\src\` | 应用主工程（Gradle 根目录） |
| `F:\Live.Android\src\app\` | 应用模块 |
| `F:\Live.Android\docs\` | 文档 |
| `F:\Volga.IRtekNetSDK\` | IRtekNetSDK 源码仓库 |
| `F:\Volga.IRtekNetSDK\android\` | SDK Android 模块（JNI + Kotlin） |
| `F:\Volga.IRtekNetSDK\src\IRtekNetSDK\` | SDK 核心 C++ 源码 |
| `F:\Volga.IRtekSDK_Android\IRtekNetSDK\demo\IRtekNetSDKDemo\` | SDK 官方 Demo（可参考） |

## 依赖关系

```
Live.Android (app)
├── IRtekNetSDK (源码模块, JNI/C++)
│   ├── NativeSDK.kt (JNI 桥接)
│   ├── NetSDKManager.kt (Kotlin API 层)
│   └── libirtek_netsdk.so (CMake 编译的原生库)
│       └── ffmpeg-kit-min-gpl:5.1.LTS (视频编解码)
├── Jetpack Compose (UI 框架)
├── Room (本地数据库)
└── Kotlin Coroutines (异步)
```

### SDK 引用方式

在 `settings.gradle` 中以源码模块引入：

```groovy
include ':IRtekNetSDK'
project(':IRtekNetSDK').projectDir = new File('F:/Volga.IRtekNetSDK/android')
```

在 `app/build.gradle` 中依赖：

```groovy
implementation project(':IRtekNetSDK')
```

> **注意**：不要使用 `IRtekNetSDK-release.aar`，该 AAR 是旧版 OkHttp/Gson 封装，存在 JSON 反序列化 bug（未提取 `data` 字段导致所有属性为空）。必须使用源码模块。

### Maven 仓库

SDK 依赖 `ffmpeg-kit`，需要额外的 Maven 仓库：

```groovy
// settings.gradle
maven { url 'https://artifactory.appodeal.com/appodeal-public' }
```

## 工具链版本

| 工具 | 版本 |
|------|------|
| Android Studio | Meerkat 或更高 |
| Gradle | 8.13 |
| Android Gradle Plugin (AGP) | 8.13.2 |
| Kotlin | 2.2.0 |
| KSP | 2.2.0-2.0.2 |
| compileSdk / targetSdk | 36 |
| minSdk | 24 |
| JVM Target | 11 |
| NDK（SDK 模块） | 27.0.12077973 |
| CMake（SDK 模块） | 3.22.1 |

## 主要依赖版本

| 依赖 | 版本 |
|------|------|
| Compose BOM | 2024.09.00 |
| Material3 | BOM 管理 |
| Room | 2.7.1 |
| Kotlin Coroutines | 1.7.3 |
| AndroidX Core KTX | 1.10.1 |
| Activity Compose | 1.8.0 |
| Lifecycle Runtime | 2.6.1 |

## 编译与部署

### 编译 Debug APK

```bash
cd F:\Live.Android\src
.\gradlew assembleDebug
```

APK 输出路径：`app\build\outputs\apk\debug\app-debug.apk`

首次编译 SDK 模块需要 CMake 构建原生库（arm64-v8a、armeabi-v7a、x86、x86_64），耗时约 2-3 分钟。后续增量编译约 3-5 秒。

### 安装到设备

```bash
# 查看已连接设备
adb devices

# 指定设备安装（多设备时需 -s 指定）
adb -s <device_serial> install -r app\build\outputs\apk\debug\app-debug.apk

# 启动应用
adb -s <device_serial> shell am start -n com.irtek.live/.MainActivity
```

### ADB 无线调试

```bash
# 配对（首次）
adb pair <ip>:<pair_port>
# 输入配对码

# 连接
adb connect <ip>:<connect_port>
```

## SDK API 要点

SDK 为单例模式（`NetSDKManager`），内部维护一个连接 handle，一次只能连接一个设备。

### 核心 API

```kotlin
// 初始化（Application.onCreate 中调用）
NetSDKManager.init()
NetSDKManager.enableLog(true)

// 登录设备
val result = NetSDKManager.login(ip, port, username, password)

// 获取设备信息 → JSONObject
val info = NetSDKManager.getDeviceInfo()
info.data?.optString("name")         // 设备名称
info.data?.optString("model")        // 型号
info.data?.optString("serial_no")    // 序列号
info.data?.optString("firmware_version") // 固件版本

// 热像截图（保存到本地文件）
NetSDKManager.getThermalCapture(mode = 0, localFilePath)

// 搜索局域网设备 → JSONArray
val devices = NetSDKManager.searchDevices(timeoutMs = 3000)

// 登出
NetSDKManager.logout()

// 清理（Application.onTerminate 中调用）
NetSDKManager.cleanup()
```

### 多设备操作模式

由于 SDK 是单 handle，操作多设备时需串行 login → 操作 → logout：

```kotlin
for (device in allDevices) {
    NetSDKManager.login(device.ip, device.port, user, pass)
    // 获取信息、截图等
    NetSDKManager.logout()
}
```

## 项目结构

```
src/app/src/main/java/com/irtek/live/
├── LiveApp.kt                  # Application，SDK 初始化
├── MainActivity.kt             # 主 Activity，导航与数据协调
├── data/
│   ├── AppDatabase.kt          # Room 数据库
│   ├── dao/
│   │   ├── DeviceDao.kt        # 设备表 DAO
│   │   └── AlarmDao.kt         # 告警表 DAO
│   └── entity/
│       ├── DeviceEntity.kt     # 设备实体
│       └── AlarmMessage.kt     # 告警消息实体
└── ui/
    ├── theme/                  # 主题、颜色、间距
    ├── devicelist/
    │   └── DeviceListScreen.kt # 设备列表主界面
    ├── adddevice/
    │   ├── AddDeviceScreen.kt  # 添加设备入口
    │   ├── ManualAddScreen.kt  # 手动添加
    │   └── OnlineAddScreen.kt  # 在线搜索添加
    ├── device/
    │   └── DeviceScreen.kt     # 设备详情
    └── components/
        └── SectionCard.kt      # 通用卡片组件
```
