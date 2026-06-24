# 口袋星奈 (Seina Chan)

![Seina Chan](apps/android/app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png)

---

## 这啥？

[Hermes Agent](https://github.com/NousResearch/hermes-agent) 是一个很优秀的 AI Agent 框架，但它只有 Web 界面和命令行。我出门在外的时候想用手机跟电脑上的 Hermes 聊天，又懒得配置各种 QQ Bot、Telegram Bot、飞书 Bot，以及我希望保持一套配置（懒得配置 Termux 上的 Hermes）……所以Vibe了个 Android App。

它通过 WebSocket + JSON-RPC 跟 Hermes Gateway 通信，本质上就是把 Hermes 的 API 包了一层聊天界面。

如果你已经在用 Hermes 的接入 Bot 并且觉得够用了——那确实没必要换这个，这个 App 本来也只是做给我自己方便用的。

## 为什么叫星奈？

因为我的 Hermes Agent 实例，它给自己取名叫「星奈」。所以这个找她聊天的 App 就叫「口袋星奈」了。

## 功能

- **聊天**：流式输出、Markdown 渲染、多轮对话、思考链折叠展示
- **工具调用**：实时展示工具调用过程，支持展开/折叠结果
- **多会话管理**：会话列表、下拉刷新
- **图片支持**：发送图片、全屏预览、历史消息图片恢复
- **模型切换**：从服务端获取可用模型列表，运行时切换
- **连接管理**：多连接配置、连接配置档案、Token 认证、连接状态监控
- **设置项**：主题切换（浅色/深色/跟随系统）、工具显示、思考链显示、自定义隐藏工具链等
- **后台保活**：前台 Service 保持 WebSocket 连接，支持后台消息接收
- **本地持久化**：消息记录本地保存（Room）、设置持久化（DataStore）
- **主题**：模仿 Claude.com 的暖色调设计

## 技术栈

| 层 | 选型 | 说明 |
|---|---|---|
| UI | Jetpack Compose + Material3 | MD3 自适应布局 |
| 架构 | MVI + ViewModel + Repository | 单向数据流 |
| DI | Hilt | `hilt-android:2.54` |
| 网络 | Ktor Client 3.0.3 | OkHttp 引擎，WebSocket 支持 |
| 序列化 | Kotlinx Serialization 1.7.3 | |
| 本地存储 | Room + DataStore Preferences | 消息存 Room，设置存 DataStore |
| 图片 | Coil 2.6.0 | Compose 原生 |
| 导航 | Navigation Compose 2.8.5 | 三条路由：connect / chat / settings |

## 构建

```bash
cd apps/android
./gradlew assembleDebug        # 构建 debug APK
./gradlew installDebug          # 安装到连接的设备/模拟器
```

需要 Android SDK 35，JDK 17+。

### 依赖

- **compileSdk = 35**, **minSdk = 26**, **targetSdk = 35**
- **Kotlin 2.0.21**, **AGP 8.7.3**, **Compose BOM 2024.12.01**
- 使用阿里云 Maven 镜像加速（已内置，国内网络友好）

## 使用

1. 装好 APK
2. 启动后输入 Hermes Gateway 的 IP 和端口（默认 `9119`）
3. 输入 Token（如果 Hermes 配置了认证的话）
4. 点「测试连接」验证，然后点「保存并连接」
5. 开始找 Hermes 聊天

如果 Hermes Gateway 在局域网运行，IP 填对应的地址就行。

**关于外出连接家里的 Hermes**：可以用 [Tailscale](https://tailscale.com) 之类的组网工具，把手机和电脑组到同一个虚拟局域网，手机上填 Tailscale 分配的 IP 即可，不用暴露公网端口。

## 关于 Hermes Agent

本项目的服务端是 [Hermes Agent](https://github.com/NousResearch/hermes-agent)（Apache 2.0），这个 App 只是它的一个客户端实现。Hermes 本身是一个基于 Nous Research 技术的 AI Agent 框架，支持多种模型（Claude、GPT、本地模型等）、工具调用、文件操作等能力。如果你对它本身感兴趣，请移步 Hermes 仓库。

## License

MIT

```
MIT License

Copyright (c) 2026 NanamiKyoka

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## 其他

- 如果你想要一个更稳定的 Hermes 手机客户端——去用 Hermes 官方的接入 Bot（QQ Bot、飞书 Bot 那些），那个肯定比这个靠谱
