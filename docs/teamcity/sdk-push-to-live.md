# TeamCity：SDK 编译后自动推送到 Live.Android

脚本在 SDK 仓库：`Volga.IRtekNetSDK/builder/push_aar_to_live.ps1`。

在 **IRtekNetSDK** 配置里，`build_netsdk.bat` 成功之后加一步 **Command Line**（Windows）。

**先把本仓库的 `IRTEK_NETSDK_VERSION` + `findProperty` 改动合进 `dev`**，再开自动推送。

## 1. Build Parameters（SDK 工程）

| Name | Type | 建议值 |
|------|------|--------|
| `LIVE_GIT_URL` | Text | `http://192.168.10.25:3000/IRtek/Live.Android.git` |
| `LIVE_GIT_BRANCH` | Text | `dev` |
| `LIVE_GIT_USER` | Text | Gitea 有写权限的用户 |
| `env.LIVE_GIT_PASSWORD` | **Password** | 该用户密码或 token（不要写进脚本） |

## 2. Build Step

- Runner: **Command Line**
- Step name: `Push AAR to Live.Android`
- Run: **If all previous steps finished successfully**
- Script:

```bat
powershell -ExecutionPolicy Bypass -File "%teamcity.build.workingDir%\builder\push_aar_to_live.ps1" -MavenDir "%teamcity.build.workingDir%\output\library\maven" -Version "%Project_Build_Version%" -LiveGitUrl "%LIVE_GIT_URL%" -LiveBranch "%LIVE_GIT_BRANCH%" -GitUser "%LIVE_GIT_USER%"
```

密码走环境变量 `LIVE_GIT_PASSWORD`。

## 3. 行为

覆盖 `src/repo/com/irtek/netsdk`，更新 `src/gradle.properties` 的 `IRTEK_NETSDK_VERSION`，commit 后 push 到 `dev`。

`Fotric.Live` 对 `dev` 开 VCS trigger 即可接着打 APK。
