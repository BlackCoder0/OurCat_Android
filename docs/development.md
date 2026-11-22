# Android 源码开发

Demo 构建固定连接本地默认地址、使用独立包名且不内置地图 Key。以下 `local.properties` 项控制普通 Debug/Release；它们不会让公开 Demo 带上作者的云端配置。

按以下顺序完成环境、后端地址和地图 Key 配置。

---

### 第一步：安装 JDK 17 和 Android Studio

本项目使用 AGP 8.2.0，Gradle 运行环境需要 **JDK 17**。源码仍以 Java 11 作为兼容级别，这两个概念不要混为一谈。

1. 安装 JDK 17，并确认 `java -version` 显示 17。
2. 下载并安装 [Android Studio](https://developer.android.com/studio)。
3. 在 Android Studio 中打开 `File → Settings → Build, Execution, Deployment → Build Tools → Gradle`，将 **Gradle JDK** 设置为 JDK 17。
4. 在 SDK Manager 中安装 **Android SDK 34**。

如果已有 Android Studio Hedgehog (2023.1.1) 或更新版本，可跳过安装步骤，但仍要确认 Gradle JDK 为 17、Android SDK 34 已安装。

---

### 第二步：打开项目

```bash
git clone https://github.com/BlackCoder0/OurCat_Android.git
```

用 Android Studio 打开项目目录（`File → Open → 选择 OurCat_Android 文件夹`），等待 Gradle 同步完成。

> 📌 首次同步会下载依赖（Retrofit、Glide、高德 SDK 等），需要联网下载，耗时取决于网络与本地缓存。

---

### 第三步：配置 `local.properties`

`local.properties` 已在 `.gitignore` 中排除，**不会提交到仓库**。新克隆项目后如果文件不存在，请在 Android 项目根目录手动新建它。

配置项分为四类：

| 配置项 | 是否必须 | 说明 |
| :--- | :--- | :--- |
| `sdk.dir` | 编译必需 | Android Studio 通常会自动写入；如果没有，Gradle 无法找到 Android SDK |
| `OURCAT_BASE_URL_DEBUG` | 模拟器可省略 | 模拟器默认使用 `http://10.0.2.2:8080/`；真机、局域网或云端必须填写实际 Backend 地址 |
| `OURCAT_AMAP_KEY_DEBUG` | 地图功能必需 | 不使用地图时可以留空；留空不影响基础页面启动 |
| `OURCAT_BASE_URL_RELEASE`、`OURCAT_AMAP_KEY_RELEASE` | Release 按功能必需 | 只有构建 Release 包时才需要；地址必须是可访问的 HTTPS Backend，高德 Key 必须匹配 Release SHA1 |

第一次只运行模拟器时，通常只需要确认 `sdk.dir`，并启动本地 Backend。没有高德 Key 也可以先编译和运行基础页面。

如果需要手动创建文件，可使用下面的内容：

```properties
# Android SDK 路径。Android Studio 通常会自动写入，不要随意修改
sdk.dir=C:/Users/你的用户名/AppData/Local/Android/Sdk

# Debug：可选。模拟器默认使用此地址
# OURCAT_BASE_URL_DEBUG=http://10.0.2.2:8080/

# 地图功能需要；没有 Key 时先留空
OURCAT_AMAP_KEY_DEBUG=
```

| 配置项 | 优先级 | 怎么填 |
| :--- | :--- | :--- |
| `sdk.dir` | 编译必需 | 填本机 Android SDK 路径；Android Studio 通常会自动生成 |
| `OURCAT_BASE_URL_DEBUG` | 模拟器可省略 | 模拟器使用默认值 `http://10.0.2.2:8080/`；真机填电脑局域网 IP；云端填自己的 HTTPS 地址 |
| `OURCAT_AMAP_KEY_DEBUG` | 地图功能必需 | 在 [高德开放平台](https://console.amap.com) 创建 Android Key，并配置应用包名和 Debug SHA1；不用地图时可留空 |
| `OURCAT_BASE_URL_RELEASE` | 正式发布必需 | 填真实可访问的 HTTPS Backend 地址；没有云端地址时不要使用 Release 包联调 |
| `OURCAT_AMAP_KEY_RELEASE` | 正式发布地图必需 | 使用 Release 签名证书对应的 SHA1 申请的高德 Android Key |

配置影响：

- 没有 `OURCAT_BASE_URL_DEBUG`：模拟器会使用默认的 `http://10.0.2.2:8080/`；真机、局域网或云端场景必须填写正确地址，否则 App 无法访问登录和业务接口。
- 没有 `OURCAT_AMAP_KEY_DEBUG`：地图、定位和依赖定位的功能不可用，但 App 不一定无法启动。
- 没有 `OURCAT_BASE_URL_RELEASE`：不能把 Release 包连接到真实 Backend；当前项目不提供公共云端地址。

获取 Debug SHA1：

Windows PowerShell：

```powershell
keytool -list -v `
  -keystore "$env:USERPROFILE\.android\debug.keystore" `
  -alias androiddebugkey `
  -storepass android
```

macOS/Linux：

```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android
```

复制输出的 `SHA1: ` 后面的值，填入高德控制台。

---

### 第四步：启动本地 Backend

**App 必须有后端才能工作**——你需要先让后端跑起来：

```powershell
# 克隆后端（如果还没有）
git clone https://github.com/BlackCoder0/OurCat_backend.git
cd OurCat_backend

# dev Profile 默认使用 H2 并关闭 AI
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

看到 `Started OurCatApplication` 后，另开终端验证：

```powershell
curl.exe http://localhost:8080/api/auth/captcha
```

如果返回包含 `key` 和 `image` 的 JSON，再回到 Android Studio：

1. 顶部工具栏选择 `app` 模块
2. 点击绿色 **▶ Run** 按钮（或 Shift+F10）
3. 选择模拟器（推荐 Pixel 系列，API 34）或 USB 连接的手机
4. 等待 APK 编译安装 → App 自动启动

### 第五步：按需启用功能

| 你想使用的功能 | 还需要什么 |
| :--- | :--- |
| 登录、基础页面、论坛和救助基础流程 | Backend 可访问，Android Debug 地址正确 |
| 地图和定位 | Android 高德 Debug Key |
| 图片、头像和帖子上传 | `demo` Backend 使用本地图片目录；生产/云端 Backend 配置阿里云 OSS |
| AI 识猫 | Backend 配置 Replicate，并打开 AI |
| 天气提醒 | Backend 默认可使用 Open-Meteo；官方灾害预警需要和风天气 JWT |

Android 端不会保存 OSS AccessKey 或 Replicate Token。它们应该配置在 Backend 服务器中。

#### 网络连接说明

| 场景 | `OURCAT_BASE_URL_DEBUG` 应填什么 | 原理 |
| :--- | :--- | :--- |
| **Android 模拟器** | `http://10.0.2.2:8080/` | 模拟器内置 NAT，`10.0.2.2` 固定指向宿主机 `localhost` |
| **同 WiFi 真机** | `http://你的局域网IP:8080/` | 手机和电脑连同一 WiFi；用 `ipconfig` 查电脑 IP |
| **云端 Backend** | `https://你的云端域名/` | 测试时填入 `OURCAT_BASE_URL_DEBUG`；正式发布填入 `OURCAT_BASE_URL_RELEASE` |

切换到云端：

1. 先在自己的云服务器启动 Backend。
2. 确认云端 Backend 可以通过 HTTPS 访问。
3. 修改 `local.properties` 中的 Base URL。
4. 重新运行 App；已经构建好的 APK 不会自动读取后来修改的配置。

公开仓库不提供当前项目的云端地址。没有自己的云端 Backend 时，请使用本地 Backend。

<details>
<summary>📡 网络拓扑示意（点击展开）</summary>

```
🖥️ 模拟器模式（最简单）
┌──────────┐   10.0.2.2:8080    ┌──────────┐
│ Android  │ ──────────────────▶│ 宿主机    │
│ 模拟器    │    (NAT 固定地址)   │ localhost │
└──────────┘                    │ :8080    │
                                └──────────┘

📱 真机 USB 调试
┌──────────┐  192.168.x.x:8080  ┌──────────┐
│ Android  │ ──────────────────▶│ 电脑      │
│ 真机      │   (同一 WiFi 下)    │ :8080    │
└──────────┘                    └──────────┘

☁️ 云端 Backend
┌──────────┐  https://你的域名   ┌──────────┐
│ Android  │ ──────────────────▶│ 服务器    │
│ 用户手机  │                    │ :8080    │
└──────────┘                    └──────────┘
```

</details>

#### 常见问题排查

| 现象 | 可能原因 | 解决办法 |
| :--- | :--- | :--- |
| Gradle 同步失败 | JDK、网络或 SDK 配置错误 | 确认 Gradle JDK 为 17，检查代理，并确认已安装 Android SDK 34 |
| App 闪退 | 设备、运行时或其他异常 | 查看 Android Studio 的 Logcat；高德 Key 缺失通常只会导致地图/定位不可用，不是本应用启动的唯一条件 |
| 接口请求失败 | 后端未启动或地址不对 | 模拟器用 `10.0.2.2`，真机用局域网 IP；curl 测试后端是否可达 |
| 地图不显示 | 高德 Key 错误 | 查看 Logcat 搜 `amap`，通常有明确错误提示 |
| 图片上传失败 | `dev`/生产 Backend 没有配置 OSS，或本地 demo 数据目录不可写 | `demo` 检查 `OURCAT_DEMO_DATA_DIR` 权限；生产检查 OSS Endpoint、Bucket 和 AccessKey |
| AI 识猫失败 | Backend 没有配置 Replicate 或 AI 被关闭 | 检查 Backend 的 `AI_ENABLED`、`AI_REPLICATE_ENABLED` 和 Token |

---
