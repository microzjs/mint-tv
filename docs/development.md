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
| Playlist / PlaylistDownload / Store / NetworkProfile | 解析、公开列表备用下载、订阅更新与省份运营商匹配 |
| Health / ProbeJob / ProbePolicy / ProbeBudget / SourceRanking | 实际试播、前后台解码预算、画质证据、隐藏规则和分辨率优选 |
| LocalState / StateDisk | 内存快照及分桶持久化，保留旧数据迁移留底 |
| Epg / EpgParser / ChannelLogo | 当前／下一节目、保守名称匹配、小尺寸台标 |
| LanImportServer / LanImportDialog | 仅临时开放的局域网导入会话和二维码 |
| TvTools / BackupData | 频道与网络设置、旧数据校验及网址校验；没有备份恢复菜单 |

测试放在 `app/src/test`，覆盖排序、回退顺序、旧检测格式、画幅、XMLTV、局域网 HTTP 会话、备份与持久化异常恢复；独立 `scripts/test-playlist.*` 覆盖列表快照和 93 种网络匹配组合。真机验证仍需要实际设备，JVM 测试不代替播放器／系统文件服务回归。

## 1.6.0 的容错与画质规则

系统更新发布时须同时提交 `releases/latest.json`，使用 GitHub Release 的 `tag_name / draft / prerelease / assets` 格式，包含 APK 名称、正式下载地址、实际大小、`state: uploaded` 与 `sha256:` 摘要。版本信息先读该静态文件，备用 jsDelivr，再尝试 API，避免共享出口的 API 配额限制。只更新正式版本信息；候选包不要更新该文件。系统安装仍要求用户授权和确认。

- 仅内置公开频道列表依次尝试 GitHub raw、jsDelivr；总下载预算 8 秒。逐个校验解析结果，全部失败不替换缓存。自定义订阅和直播请求不转发给镜像。
- 设置显示最近成功列表的下载来源；缓存与安装包内的福建移动列表继续作为本地回退。备用列表不保证跨运营商可播。
- 前台使用解码器前，同步取消后台硬解试播；后台遇到软件解码器不足时，最多追加一次 5 秒硬解尝试。前台播放时不抢硬解资源。
- 试播超时及解码资源不足属于“不确定”，不作为隐藏频道的依据；确认网络或源错误仍计失败。
- 实际观看首帧成功后，将该源及分辨率写入当前网络的画质缓存，不强制打断当前频道切源。
- 退出到后台后，可对仍未验证的源补验；同一网络最多每天安排一次，已确认的源不重扫。系统调度可能推迟执行；强制停止应用时不保证后台作业运行。
- 主界面“重试”和空频道行提供恢复观看入口；保留现有分类、单行频道条目与地方台画幅修正。
- 已移除未使用的历史模块、相关旧测试、组播锁及对应权限；保留 LocalState 仍引用的数据校验辅助方法。


## 1.6.4 首次启动与焦点规则

- APK 的 assets 中不再包含频道 TXT；历史福建列表移至 tests/fixtures，仅供解析回归，不能重新打包进应用。Store.activeProfile 在未识别／未下载时可为空，Health 与界面必须处理该状态。
- 自动匹配列表仅从 GitHub raw 请求，总下载预算 8 秒；不通过 jsDelivr 补齐。系统更新的版本清单备用地址与直播请求策略不受此修改影响。
- 初次识别失败不选择福建，下载失败不制造频道。已有缓存和用户导入保留。
- 主页频道列表右键进入线路，三个控制按钮左右连接；左侧边界回频道、下键回频道，上键进入设置。顶部按钮下键有明确回路；网络状态也有可见焦点。
- 详情展开时重置 60 秒无操作计时，普通选台为 30 秒；每次按键／触屏重新计时。
- 1.6.4 的首次无代理测试由用户操作：用户卸载旧版后才允许安装，安装后不得自动启动或触发抓源。
