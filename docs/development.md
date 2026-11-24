# 开发、构建与签名

环境：JDK 17、Android SDK 34、Build Tools 34.0.0、Gradle 8.9、Android Gradle Plugin 8.5.2。

```powershell
.\gradlew.bat --no-daemon :app:assembleCompat :app:assembleRelease :app:lintDebug :app:lintRelease :app:testDebugUnitTest
.\scripts\test-playlist.ps1
```

Linux / macOS 使用 `./gradlew` 及 `bash scripts/test-playlist.sh`。

`Compat` 继承 Release 的 R8 和资源压缩，使用本机原 debug key，供现有开发版覆盖回归；仅同签名构建才可覆盖。安装前保留数据并由设备用户确认：

```sh
adb install -r app/build/outputs/apk/compat/app-compat.apk
```

正式 Release 通过四项本地环境变量配置独立签名：`MINT_KEYSTORE`、`MINT_STORE_PASSWORD`、`MINT_KEY_ALIAS`、`MINT_KEY_PASSWORD`。四项需同时设置，全部未设置时只生成 unsigned Release，不回退到 debug key。密钥、环境文件和口令不得入库。换证书通常无法覆盖原开发版，应先备份，由用户决定迁移。

## 结构

| 模块 | 作用 |
| --- | --- |
| MainActivity / TvPlayerView | 原生电视界面、遥控器、播放和按频道画幅 |
| Playlist / Store / NetworkProfile | 解析、订阅更新与省份运营商匹配 |
| Health / ProbeJob / SourceRanking | 实际试播、缓存、隐藏规则和分辨率优选 |
| LocalState / StateDisk | 内存快照及分桶持久化，保留旧数据迁移留底 |
| Epg / EpgParser / ChannelLogo | 当前／下一节目、保守名称匹配、小尺寸台标 |
| LanImportServer / LanImportDialog | 仅临时开放的局域网导入会话和二维码 |
| BackupData / PublicBackups / TvTools | 校验后的备份恢复、电视下载目录与设置 |

测试放在 `app/src/test`，覆盖排序、回退顺序、旧检测格式、画幅、XMLTV、局域网 HTTP 会话、备份与持久化异常恢复；独立 `scripts/test-playlist.*` 覆盖列表快照和 93 种网络匹配组合。真机验证仍需要实际设备，JVM 测试不代替播放器／系统文件服务回归。
