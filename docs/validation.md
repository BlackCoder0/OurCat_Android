# Demo 验证记录

验证日期：2026-11-22。测试设备为 Windows 上的 Pixel 3 API 35 模拟器，后端为配套 demo H2 本地实例。具体源码提交和产物哈希见发行包 `build-info.json`。

## 已通过

- JDK 17、Android SDK 34 下执行 `gradlew.bat assembleDemo assembleDebug processReleaseManifestForPackage` 成功。
- Demo 独立包名为 `com.ourcat.app.demo`；初始地址为 `http://10.0.2.2:8080/`，地图 Key 为空，不继承私人 Debug 配置。
- Release 最终清单保持 `usesCleartextTraffic=false`；Demo 允许本地 HTTP。
- 实际修改后端地址、重新打开确认保存、切回有效实例并读取验证码。
- 使用正常图片验证码登录本地管理员，显示资料和示例内容。
- 从系统文件选择器选图、上传并保存头像；选择 PNG 后确认服务端文件是真实 JPEG。
- 实际通过界面发布带图论坛帖子，后端确认帖子及本地图片地址已保存。
- 实际拍照后进入猫咪上报页，看到示例位置说明并成功提交；后端重启后仍能查询到该记录，坐标为 31.23、121.47。
- 切换到未启动的本地端口后，登录页显示连接失败及排查提示；再切回有效地址重新获取验证码。
- URL 校验的独立断言检查通过，覆盖协议、根路径、端口、用户信息、查询和片段等输入。

## 复核命令

在仓库根目录执行（Windows PowerShell，JDK 17）：

```powershell
.\gradlew.bat assembleDemo assembleDebug processReleaseManifestForPackage
javac -encoding UTF-8 -d app/build/address-check app/src/main/java/com/ourcat/app/utils/ServerAddress.java tools/ServerAddressCheck.java
java -ea -cp app/build/address-check ServerAddressCheck
```

后端的组织审核、任务分配/申领/日志/完成与越权拒绝，通过后端 MockMvc 测试；不宣称所有对应 Android 页面都已手工验收。

## 尚未验证

- 实体手机、其他 Android 版本、陌生电脑上的首次安装与真实局域网连接。
- 全部角色页面逐项手工操作、慢网络下所有页面的实例切换竞态。
- 地图、定位、图片 AI、生产 OSS、付费天气；Demo 使用示例位置并关闭 AI。
- 生产 MySQL 配套回归、公网运营、跨版本升级和正式签名分发。

此版用于学习和本地试用。Demo 是可调试构建，不能作为已经完成审核、准备承载公共用户的正式 App。

## 2026-11-23 补充复核（preview.2）

- 将已发布 preview.1 ZIP 重新完整解压到新目录，使用包内 Java 启动独立 H2 实例；未使用线上数据库。
- 在同一 API 35 模拟器重现论坛 PNG 上传返回 400：客户端生成 JPEG，论坛与广场仍声明原图类型。
- preview.2 修正两处上传文件名和类型声明，均与 ImageUtils 输出的 JPEG 一致。没有修改服务端图片校验。
- 重新构建 Demo / Debug 和 Release 合并清单；Release 仍禁止明文 HTTP。后端 `mvn -o package` 的 9 项测试全部通过。
- 安装修复 APK，分别用系统选择器选择同一 PNG，实际发布论坛和广场。断言两条记录各保存一次，图片 GET 返回 image/jpeg，图片可解码为 JPEG，内容与本地文件一致。
- 再次使用正常验证码登录、查看猫咪列表、拍照并提交上报；后端确认示例坐标为 31.23、121.47。
- 本轮截图与剪辑录屏见[Demo 实拍](demo-preview.md)。论坛列表返回后需下拉刷新时，已在说明中列出。

以上仍是在原 Windows 电脑和模拟器完成，不能替代另一台电脑、实体手机、独立新手或生产服务验收。preview.1 附件保持原样，包含修复的 APK 随 preview.2 发布。
