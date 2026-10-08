# SystemUI 切换器

独立 Android 应用，包名 `dev.glass.systemuiswitcher`，不依赖音量模块或 LSPosed。需要用户在 root 管理器中授权。

## 支持范围

本应用针对本项目提供的两份 SystemUI 17.99.02 APK：

- Ace 6：`29ff19621cb37017cfd195109e2b4ed202175c72c1a0d247519ec866d1dabcbe`
- 一加 15：`31a3d5ad620488c64a77ac8a08b051686b2e177d84445c614cb904a129391a77`

必须是 API 37，且 `/system_ext/priv-app/SystemUI/SystemUI.apk` 的 SHA-256 与上述 Ace 6 原版一致。以文件内容校验作为切换门槛，不绑定 IMEI、序列号、ADB 地址或具体机型名称。不同 ROM 的 APK 不匹配时禁用切换。

## 操作

1. 点击检测并授权 root。
2. 选择切换到一加 15，或恢复 Ace 6。
3. 操作准备成功后点击“立即重启并生效”。重新打开应用，会核验 APK 内容、安装路径和 SystemUI 进程实际加载路径。
4. 尚未重启时，可点击“撤销待重启操作”。

一加 15 APK 内置在应用中，安装后无需电脑、ADB 或外部文件。恢复 Ace 6 使用 ROM 自带原版，因此不重复安装此前被 ColorOS 拒绝的机密 APK。

## 实现

- 一加 15：复制内置 APK、核对 SHA-256，再执行 root 分阶段安装，确认存在校验就绪的 SystemUI 会话。
- Ace 6：核对系统原版和当前更新，备份包管理元数据，将当前更新目录移出 `/data/app`，重启后由包管理器回到系统版本。
- 备份保存在 `/data/local/tmp/systemui-switcher-backups/restore-*`。不直接改写系统 APK、包管理 XML 或应用数据。
- 撤销恢复操作只允许在同一次启动中，且备份内容和路径必须匹配；重启后不能直接放回旧目录。
- 待重启状态保存在应用私有存储中；操作失败或 root 拒绝时显示记录，不自动重复操作。
- 现有 LSPosed 音量模块仍可能影响显示样式；APK 切换不等于关闭模块，也不证明跨机型视觉完全兼容。

## 构建

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease
```

构建时从父项目目录复制所提供的一加 15 APK到生成的 assets；release 签名复用父项目的 `signing.properties`，路径仅用于本地构建。原有音量模块的源码、构建配置均不改动。

## 验证

单元测试覆盖：未知 ROM/SDK 拒绝、非预期安装路径拒绝、路径穿越和其他包拒绝、仅取消未生效的 SystemUI 会话且区分就绪状态、shell 参数引用。实机操作结果另外记录，不以构建成功代替切换生效。

2026-10-06 构建的 0.1.0：release 构建及签名校验通过，6 项单元测试通过，内置 APK 的 SHA-256 与项目原文件一致。API 36 模拟器安装、启动、无 root 的中文错误提示及禁用切换按钮验证通过；未发现 AndroidRuntime 崩溃。实机目前未连接，尚未验证本应用的 root 切换及重启生效。

交付包：`dist/SystemUI-switcher-0.1.0.apk`，校验记录：`dist/verification.json`。
