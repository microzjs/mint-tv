薄荷电视 1.0.0

面向 Android 电视盒子，也可在手机横屏使用。已安装到连接的小米 14。

主要功能
• 默认订阅 xisohi/CHINA-IPTV 的 Unicast/fujian/mobile.txt，当前为 119 个频道、244 条去重线路。
• 同名频道合并，按源列表原顺序尝试；播放报错、14 秒无首帧或缓冲超时时尝试下一条。全部失败后可重试或手动选线。
• 每天由 Android JobScheduler 在联网时尝试更新，系统休眠或断网可能延后；打开应用距上次尝试超过一天时补更。失败保留缓存。
• 支持 M3U/M3U8/TXT 文件导入、HTTP/HTTPS 订阅网址导入、导入管理、频道搜索及收藏。订阅网址随每日更新，本地文件保持导入内容。
• 内置列表快照，因此网络更新失败仍可显示频道；能否播放取决于线路与当前网络。

操作
• 方向键移动焦点，确定键播放频道；选择预览右下角“全屏”放大。
• 全屏时：上下换台、左键选线路、右键回列表、确定键打开播放菜单、返回键退出全屏。
• 菜单键打开线路列表（手机可能由系统截获菜单键，请点击“线路”）。
• 线路可随时手动切换，收藏按钮添加/取消当前频道收藏。
• 右上角“导入”选择网址或文件；“设置”可立即更新和查看更新状态。

播放范围
使用 AndroidX Media3，包含 HLS、DASH 及常见 HTTP/UDP 媒体支持。RTP/RTSP 地址会跳过，请先转换为可用的 HTTP 代理源。运营商频道可能限定对应的福建移动宽带网络。没有配置节目单 EPG、回看、录制及远程推送。

实测（2026-11-21，小米 14）
• CCTV1、CCTV2、安徽卫视出现首帧并播放。
• 测试列表第一条地址返回 HTTP 错误后，自动播放第二条，日志 FIRST_FRAME source=1。
• 网址订阅、本地 TXT 导入、导入移除及全屏进出通过。
• 每日持久化任务已注册，并强制运行验证了源下载及缓存更新。
• 解析器测试通过，Gradle release 构建和 Android Lint 通过（0 错误，存在非阻断提示）。
• 测试频道和测试订阅已移除。未对全部 244 条线路逐条测速，尚未在实体电视盒子实测遥控器。

项目来源
频道源：https://github.com/xisohi/CHINA-IPTV
界面风格参考：https://github.com/MoonTechLab/LunaTV/releases
应用为新写的原生 Android 实现，未复制 LunaTV 源码或图片。

源码构建
需要 JDK 17、Android SDK 34。配置 ANDROID_HOME 或 local.properties 中的 sdk.dir。
在源码目录执行 gradlew.bat assembleDebug 或 gradlew.bat assembleRelease。
默认 release 使用本机 Android debug keystore 自签名，便于个人安装；重新构建的 APK 若签名不同，不能直接覆盖已安装版本。
最低 Android 6.0（API 23），目标 API 34。
解析测试：用 javac 编译 app/src/main/java/cn/mint/tv/Playlist.java 和 tests/PlaylistTest.java，然后运行 PlaylistTest 并传入 assets/fujian-mobile.txt 的路径。
