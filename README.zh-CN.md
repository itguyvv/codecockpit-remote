# CodeCockpit Remote

[![CI](https://github.com/ITGuyVV/codecockpit-remote/actions/workflows/ci.yml/badge.svg)](https://github.com/ITGuyVV/codecockpit-remote/actions/workflows/ci.yml)
[![GitHub release](https://img.shields.io/github/v/release/ITGuyVV/codecockpit-remote?sort=semver)](https://github.com/ITGuyVV/codecockpit-remote/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/ITGuyVV/codecockpit-remote/total)](https://github.com/ITGuyVV/codecockpit-remote/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**[English](README.md)**

[CodeCockpit](https://github.com/ITGuyVV/CodeCockpit) 的官方移动端伴侣应用——在手机上监控并驱动你的 AI 编码 CLI 会话（Claude Code / Codex / OpenCode）。设备直连：局域网、Tailscale 或跨网 IPv6 直连。**永不依赖云服务器，永不建公共中继。**

本仓库收纳全部移动端客户端，按平台分目录：**`android/`**（Kotlin + Jetpack Compose，现已可用），`ios/` 与 `harmony/` 为后续客户端预留。

## 功能

- **实时会话列表** —— 桌面上每个会话：引擎（claude / codex / opencode）、标题、工作目录、运行/忙碌/已退出状态、模型、费用。
- **工作台主页** —— 会话以紧凑方块呈现并带实时状态点；侧滑抽屉按文件夹分组。可新建会话（引擎、权限模式、文件夹）、重命名、停止，也可浏览桌面的历史会话并一键恢复——对话与统计秒级重建。
- **聊天式监控** —— 会话对话以气泡形式流式推送到手机；工具调用显示为紧凑的可展开行（含捕获的输出），计划/任务更新渲染为进度卡片。
- **随处发提示词** —— 在聊天框输入，提示词直接写入会话的 PTY，和在桌面上手敲完全一致。agent 运行时可点红色停止键（发送 Esc）。
- **跟随输出** —— 输出时聊天自动跟随滚动，上滑即让位，一键回到底部。
- **权限模式切换** —— 按会话切换 Ask / Accept edits / Plan / Bypass permissions。
- **实时统计** —— 模型、输入/输出 token、费用、轮次随 agent 工作实时更新。
- **稳定连接** —— 指数退避自动重连；桌面吊销令牌时用保存的密钥自动重新配对。

## 安全模型

远程通道为"开发者自己的设备 + 可信网络"设计：

1. **TLS + 指纹锁定。** 桌面端使用自签证书。首次连接时应用展示证书的 SHA-256 指纹——与桌面端 *Settings → Remote access* 页显示的指纹人工比对后信任（TOFU）。之后指纹按桌面锁定，指纹变化即拒绝连接。
2. **配对密钥 → 设备令牌。** 首次连接使用桌面显示的配对密钥。桌面给设备签发随机令牌且只存其哈希。应用后续用令牌重连，不再发送密钥——除非桌面吊销了令牌，此时应用自动重新配对。
3. **密钥留在设备上。** 密钥、令牌与锁定的指纹存放在应用私有 DataStore，并已排除出 Android 云备份与设备迁移。
4. **无云、无中继。** 流量只在手机 ↔ 桌面之间，走局域网、tailnet 或 IPv6 直连。双方通信协议[公开成文](https://github.com/ITGuyVV/CodeCockpit/blob/main/docs/REMOTE_PROTOCOL.md)。

## 配置步骤

**前提**

- 桌面装有 [CodeCockpit](https://github.com/ITGuyVV/CodeCockpit) 且已启用 **Remote access** 功能
- 手机 Android 8.0+

**步骤**

1. 桌面端打开 *Settings → Remote access*，启用并复制**配对密钥**，记下端口（默认 `47315`）。Windows 首次启用会弹防火墙授权——放行即可。
2. 安装应用（[Releases](https://github.com/ITGuyVV/codecockpit-remote/releases) 或自行构建）。
3. 点 **+**，选择场景：*同一网络* 填桌面局域网 IP（同一设置页有列出）、端口和密钥；*IPv6 直连* 填桌面的公网 IPv6 地址和密钥。起个名字。
4. 首次连接时应用展示**证书指纹**——与桌面 Remote access 页显示的指纹比对无误后点 *Trust*。
5. 完成——会话列表出现，点进任意会话开始聊天监控。

**出门在外？**

在桌面和手机都装上 [Tailscale](https://tailscale.com)，然后用桌面的 tailnet IP 添加（桌面设置页已帮你标出 Tailscale 地址）。连接方式完全相同——同样的 TLS 指纹锁定、同样的密钥，不向互联网暴露任何端口。

两端都有公网 IPv6 时也可以不装 VPN 直接跨网连接——用桌面的公网 IPv6 地址添加（设置页列出地址并标明 IPv6 当前是否可用）。

## 从源码构建（Android）

```bash
git clone https://github.com/ITGuyVV/codecockpit-remote
cd codecockpit-remote/android
./gradlew assembleDebug     # app/build/outputs/apk/debug/
```

或用 Android Studio（Ladybug+）打开 `android/` 目录直接运行。要求：JDK 17、Android SDK 35、Android 8.0+ 设备（minSdk 26）。仓库结构与约定见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 路线图

- **M2** —— 已退出会话的历史对话（桌面端磁盘快照端点）
- **M3** —— ~~手机端创建/管理会话~~ —— 已于 v0.3.0 完成：新建/重命名/停止会话、历史浏览与恢复、计划卡片、聊天内工具输出
- **M4** —— ~~局域网/Tailscale 之外的打洞直连（WebRTC）~~ —— 已于 v0.3.0 改道：**IPv6 直连**覆盖跨网场景且零中继基础设施（两端需有 IPv6）；通用方案仍是 Tailscale
- **iOS / HarmonyOS 客户端** —— 规划中；iOS 免费路线 = PWA

## 许可证

[MIT](LICENSE)
