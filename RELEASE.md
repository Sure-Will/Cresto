# Cresto 发版与应用内更新

本仓库是 [Nevodev/Cresto](https://github.com/Nevodev/Cresto)（Apache-2.0）的自用修改版，安装包与原版分开发布、分开更新。

## 更新链路

- 安装包发布在公开仓库 `Sure-Will/Cresto-Releases`，只放 APK、`.sha256` 和 `update.json`，不放源码。
- release 包免登录读取 `https://github.com/Sure-Will/Cresto-Releases/releases/latest/download/update.json`；debug 包不检查更新。
- 打开「启动时检查更新」后，每 24 小时最多自动检查一次，发现新版本弹出更新页；「关于」页可手动检查。
- 点「更新」：缺「安装未知应用」权限先跳系统设置，回来自动继续 → 下载到 App 私有目录 → 校验大小、SHA-256、包名、版本号、minSdk、签名证书 → PackageInstaller 调起系统安装确认。下载和校验中可以取消；失败时可重试或用浏览器下载。
- 清单的 `downloadUrl` 必须是同一 tag 下的 `releases/download/<tag>/*.apk`，`releaseUrl` 必须是该 tag 的页面；不接受 `latest`、其他域名或其他仓库。规则在 `UpdatePolicy`，测试在 `UpdatePolicyTest`。

## 签名身份

- applicationId：`com.sure.cresto`（debug 为 `com.sure.cresto.debug`，可与 release 同时安装）
- keystore：`~/.android/cresto-release.jks`，alias `cresto`；密码在仓库根目录 `keystore.properties`（不入库），格式：

  ```properties
  storeFile=/absolute/path/to/cresto-release.jks
  storePassword=...
  keyAlias=cresto
  keyPassword=...
  ```

- 证书 SHA-256：`81335eb1f6646f92d4f46a9b6ed555d51145f0cbfebe4f5afc626e587f928105`

keystore 和密码丢了，就不能再覆盖升级，只能卸载重装（数据会丢）。两者要一起备份到私密位置。没有 `keystore.properties` 时 release 包不签名。

## 发一个新版本

1. 提交要发布的代码。版本号来自 `app/version.properties`：`VERSION_CODE` 由 release 构建自动 +1，`VERSION_NAME` 需要时手动改。
2. 写更新说明，例如 `/tmp/cresto-notes.md`：

   ```markdown
   一句话简介（可选）
   ## 新功能
   - 条目
   ## 修复
   - 条目
   ```

   标题含「新功能 / 修复 / 改进 / 优化」时会显示对应图标。

3. 准备产物：

   ```bash
   bash scripts/prepare-release.sh /tmp/cresto-notes.md
   ```

   脚本依次检查：工作区已提交、单元测试、release 构建、包名、只含 arm64-v8a、签名证书指纹、versionCode 高于线上最新、tag 未发布过。产物写入 `dist/v<版本名>-<版本号>/`。构建结束后 `app/version.properties` 会自动 +1，提交这个改动。

4. 按脚本最后打印的命令，先建草稿 release；核对草稿里的资产大小和哈希后，再发布并设为 latest。
5. 匿名下载 `latest/download/update.json` 和 APK，确认哈希与本地一致。发布后几分钟内 `latest/download` 可能在新旧版本间来回，多取几次。

## 首次安装

应用内更新只能从已安装的 `com.sure.cresto` 升级。第一次需要手动安装：`adb install` release APK，或从 Releases 页面下载后安装。它和原版 `com.nevoit.cresto` 是两个应用，数据不互通，可以同时存在。
