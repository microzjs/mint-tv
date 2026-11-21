# 薄荷电视 · Mint TV

原生 Android IPTV 播放器，面向电视盒子和遥控器操作，兼容手机横屏使用。默认订阅福建移动频道源。

![实机界面](docs/screenshot.jpg)

## 功能

- 从 [CHINA-IPTV](https://github.com/xisohi/CHINA-IPTV) 的 `Unicast/fujian/mobile.txt` 每日尝试更新；失败保留缓存，启动时补更。
- 同名频道合并多条线路，按原始顺序实际试播；报错、14 秒无首帧或缓冲超时自动切换到下一条，也可手动选择。
- 频道分组、搜索、收藏、预览与全屏播放。
- 导入 M3U / M3U8 / TXT 文件和 HTTP/HTTPS 订阅网址；支持 UTF-8 和 GB18030 编码。网址随每日任务更新，本地文件保留导入内容。
- Android TV 启动入口、方向键焦点和换台操作。

每日更新由 Android JobScheduler 调度，断网、休眠或系统后台限制会延后执行。运营商源能否播放取决于当前网络，部分源限福建移动宽带。内置列表快照在 2026-11-21 解析为 119 个频道、244 条线路，后续数量可能变化。

## 开发与构建

环境：JDK 17、Android SDK Platform 34、Build Tools 34.0.0。Gradle Wrapper 已包含在仓库，版本 8.9；Android Gradle Plugin 8.5.2。

1. 克隆本仓库，在 Android Studio 中打开根目录，或配置 `ANDROID_HOME` 指向本机 SDK。
2. 也可在本地新建 `local.properties`，设置 `sdk.dir`；该文件不提交。
3. 执行构建及检查：

```bash
# macOS / Linux
./gradlew :app:assembleDebug :app:lintDebug
bash scripts/test-playlist.sh

# Windows PowerShell
.\gradlew.bat :app:assembleDebug :app:lintDebug
.\scripts\test-playlist.ps1
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。安装：`adb install -r app/build/outputs/apk/debug/app-debug.apk`。

最低 Android 6.0（API 23），目标 API 34，包名 `cn.mint.tv`。

## GitHub 自动构建

推送、Pull Request 或在 Actions 页面手动运行 **Android build**，会运行解析测试、Android Lint 并构建 Debug APK。成功后在对应运行的 **Artifacts** 下载 `mint-tv-debug`。

调试包及当前 release 配置使用构建机器的 Android debug keystore。不同机器或 CI 生成的签名可能不同，不能保证覆盖手机已安装版本。需要稳定发布时，应自行配置独立 release 签名，并通过本地环境或 GitHub Secrets 提供密钥；不要把签名文件提交到仓库。

## 代码结构

| 路径 | 作用 |
| --- | --- |
| `app/src/main/java/cn/mint/tv/MainActivity.java` | 原生界面、遥控器交互、播放器和线路切换 |
| `app/src/main/java/cn/mint/tv/Playlist.java` | M3U/TXT 解析、频道合并和去重 |
| `app/src/main/java/cn/mint/tv/Store.java` | 订阅、缓存、导入与更新任务配置 |
| `app/src/main/java/cn/mint/tv/UpdateJob.java` | Android 每日后台更新任务 |
| `app/src/main/res/drawable/` | 原创薄荷电视图标和 TV banner |
| `tests/PlaylistTest.java` | 独立于 Android 的解析测试 |

## 遥控器与使用

方向键移动焦点，确定键播放，预览右下角按钮进入全屏。全屏时上下换台、左键选线路、右键返回列表、确定键打开播放菜单，返回键退出全屏。菜单键可打开线路列表；手机可能截获该键，此时点击“线路”。右上角可导入、管理订阅或立即更新。详见 [使用说明](README.txt)。

## 验证与当前范围

2026-11-21 在小米 14 上验证了 CCTV1、CCTV2、安徽卫视播放，以及失效首源到第二条线路的自动切换、手动选线、全屏进出、网址和本地 TXT 导入、移除导入、更新任务及缓存。解析器测试和本地 release 构建、Lint 已通过。

尚未逐条测试全部线路，尚未在实体电视盒子测试。包含 HLS、DASH 及常见 HTTP/UDP 媒体支持；RTP/RTSP 目前跳过，需要转换为 HTTP 代理源。不含 EPG、回看、录制功能。

频道数据来自 [xisohi/CHINA-IPTV](https://github.com/xisohi/CHINA-IPTV)，界面风格参考 [LunaTV](https://github.com/MoonTechLab/LunaTV/releases)，播放器使用 [AndroidX Media3](https://github.com/androidx/media)。应用为原生新实现，未复制 LunaTV 源码或图片。

