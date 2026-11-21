# 屏幕方向回归记录 · 1.0.1

## 原因与设备恢复

2026-11-21，在小米 14（Android 16）复现了退出播放器后 Clash 和 Gmail 仍横屏的现象。设备当时的系统状态为 `accelerometer_rotation=0`、`user_rotation=1`，即关闭自动旋转且锁定方向为横屏。

进一步控制变量测试确认：在系统竖屏锁定、播放器正在横屏显示时执行 `adb shell uiautomator dump`，命令结束后 `user_rotation` 会从 `0` 变为 `1`。此前界面验收使用了此命令，留下了错误的系统锁定方向。应用本身没有写入系统旋转设置，也没有申请 WRITE_SETTINGS 权限。

AOSP 的 [UiAutomationConnection](https://github.com/aosp-mirror/platform_frameworks_base/blob/main/core/java/android/app/UiAutomationConnection.java) 在连接时保存实际显示方向，在断开时恢复冻结方向；这解释了实际横屏显示与用户原有竖屏锁定不一致时的副作用。

本次通过 ADB 单次恢复 `user_rotation=0`，保留 `accelerometer_rotation=0`。这项恢复属于调试设备状态修复，没有加入应用代码，也未增加权限。

## 应用调整

- 移除 Manifest 中永久的 landscape 声明。
- 前台 onCreate / onResume 请求横屏；onPause 立即改回 UNSPECIFIED，让接下来的界面自行决定方向。
- 退出使用 finishAndRemoveTask，移除已退出的任务。
- 屏幕配置变化时重新渲染频道卡片列数；保留播放器与订阅逻辑。

## 真机回归

测试初始条件：手机已解锁、自动旋转关闭、系统竖屏锁定（0/0）。观察使用 `dumpsys window displays`、`settings get` 和 `screencap`；最终验收没有再运行 UIAutomator。

| 操作 | 实际显示方向 | 结果 |
| --- | --- | --- |
| 打开薄荷电视 | 横屏，rotation=1 | 通过 |
| Home 最小化后打开 Clash Meta | 竖屏，rotation=0 | 通过 |
| 直接从薄荷电视切至 Gmail | 竖屏，rotation=0 | 通过 |
| 直接切至另一个 Clash 客户端 | 竖屏，rotation=0 | 通过 |
| 返回薄荷电视 | 横屏，rotation=1 | 通过 |
| 全屏观看后 Home 最小化，再开 Gmail | 竖屏，rotation=0 | 通过 |
| 恢复全屏，返回列表，再确认退出 | 应用 Activity 和任务移除 | 通过 |
| 确认退出后打开 Gmail | 竖屏，rotation=0 | 通过 |
| 退出后重新打开，再最小化到 Clash Meta | 应用横屏、Clash 竖屏 | 通过 |
| 最后返回桌面 | 竖屏，rotation=0 | 通过 |

以上过程中的系统设置持续保持 `accelerometer_rotation=0`、`user_rotation=0`。CCTV1 在新版本播放并出画面；解析测试、release 构建和 Android Lint 通过。此轮没有实体电视盒子可供回归。

## 后续调试注意

避免在旋转锁定与前台显示方向不一致时使用 UIAutomator；需要界面检查可使用原生 screencap。若必须使用自动化，应先记录两个系统旋转设置，并在测试结束后恢复它们，然后再做跨应用方向验收。不要把界面自动化遗留的设备状态误判为应用持续改写系统方向。
