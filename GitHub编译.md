# GitHub Actions 自动编译 Release（小白保姆级教程）

你只需要：

1. 把源码上传到 GitHub（第一次）
2. 配置签名（第一次）
3. 以后每次发新版：**打 tag → push tag**，GitHub 自动编译并在 Release 页面生成 APK

本项目目标：Android 4.4.2（minSdk=19）。

---

## 0. 你需要安装哪些软件（只做一次）

下面 2 个任选其一：

### 方案 A（推荐）：安装 GitHub Desktop（最简单）

1) 下载并安装 GitHub Desktop：
https://desktop.github.com/

2) 打开 GitHub Desktop → 登录你的 GitHub 账号

> GitHub Desktop 负责：把代码上传到 GitHub、推送 tag。

另外：

- **你不需要安装 Android Studio，也不需要在本地安装 Gradle/SDK 来编译**。
- **GitHub Actions 会在云端完成编译**。

### 方案 B：安装 Git（命令行）

1) 下载并安装 Git：
https://git-scm.com/downloads

2) 安装完成后打开：Windows 的 “PowerShell” 或 “Git Bash”。

---

## 1. 第一次：把源码上传到你的 GitHub 空仓库

你的仓库已经创建好了（空的）。下面以仓库地址为例：

```
https://github.com/<你的用户名>/<你的仓库名>
```

### 1.1 方式 A：用 GitHub Desktop 上传（推荐）

1) GitHub Desktop → `File` → `Add local repository...`

2) 选择你本地项目目录（就是包含 `app/`、`build.gradle` 的那个目录）

3) 如果提示 “This directory does not appear to be a Git repository”
   - 点击 `Create a repository here...`
   - Name 填：`Nostalgia-AI`
   - 点击创建

4) 回到 GitHub Desktop 主界面，你会看到很多改动文件
   - Summary 随便写：`init`
   - 点击 `Commit to main`

5) 点击 `Publish repository`
   - 勾选/取消勾选都行（建议不勾选 private）
   - Publish

完成后，你的 GitHub 仓库就有代码了。

### 1.2 方式 B：用命令行上传（完整一行命令）

打开 PowerShell，进入你的项目目录（把路径换成你的真实路径）：

```powershell
cd "C:\Users\Kevin\Music\Nostalgia-AI"
```

初始化 git 仓库并提交：

```powershell
git init
```

```powershell
git add .
```

```powershell
git commit -m "init"
```

把远程仓库地址加进去（把 URL 换成你自己的）：

```powershell
git remote add origin https://github.com/<你的用户名>/<你的仓库名>.git
```

推送到 GitHub：

```powershell
git branch -M main
```

```powershell
git push -u origin main

（强制推送）git push -f origin main
```

> 如果提示要登录：按提示在浏览器登录 GitHub。

---

## 2. 第一次：配置签名（必须做，否则 Release 可能无法安装/更新）

Android 的 Release APK 一般必须签名。

你要准备 4 个信息：

- keystore 文件（我们生成 `release.keystore`）
- keystore 密码（store password）
- key alias
- key 密码（key password）

### 2.1 生成 keystore（命令一行，复制即可）

#### Windows PowerShell（推荐）

请在项目目录执行（会在当前目录生成 `release.keystore`）：

```powershell
keytool -genkeypair -v -keystore release.keystore -storetype JKS -alias nostalgia -keyalg RSA -keysize 2048 -validity 10000
```

如果你希望一次性把信息也写进去（仍然是一行命令），可以用下面这种（把引号里的内容改成你自己的）：

```powershell
keytool -genkeypair -v -keystore release.keystore -storetype JKS -alias nostalgia -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Nostalgia-AI, OU=Dev, O=Nostalgia, L=Beijing, ST=Beijing, C=CN"
```

执行时会让你输入密码、名字等：

- 密码请你自己设置并记住
- 其它信息随便填或直接回车

> 重要：**release.keystore 一旦丢了，你以后就无法用同一个签名更新应用**。请备份。

#### 如果提示找不到 keytool

说明你没有安装 JDK。

你可以安装：

- Temurin JDK 11（推荐）：https://adoptium.net/

安装完重开 PowerShell 再执行上面的 keytool 命令。

> 你只需要 JDK 来生成 keystore（签名文件）。这不是“编译环境”。
> 生成完 keystore 后，后续 APK 编译仍然完全在 GitHub 上进行。

### 2.2 把 keystore 转成 base64（用于 GitHub Secrets）

#### Windows PowerShell（一行命令）

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Out-File -Encoding ascii release.keystore.b64
```

执行后会生成 `release.keystore.b64`。

打开它，复制里面全部内容备用。

### 2.3 在 GitHub 仓库设置 Secrets（非常关键）

进入你的 GitHub 仓库网页：

`Settings` → `Secrets and variables` → `Actions` → `New repository secret`

你要创建 **4 个 secrets + 1 个 keystore base64**：

| Secret 名称 | 你要填什么 |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | 你刚复制的 base64 内容（很长的一段） |
| `SIGNING_STORE_PASSWORD` | 生成 keystore 时输入的 “keystore 密码” |
| `SIGNING_KEY_ALIAS` | 你设置的 alias（本教程是 `nostalgia`） |
| `SIGNING_KEY_PASSWORD` | 生成 key 时输入的 “key 密码”（通常和 store 密码一样） |

> 注意：Secrets 里的内容不要加引号，不要加空格。

---

## 3. 第一次：确认 GitHub Actions 已启用

进入仓库：`Actions` 标签页。

- 如果显示需要启用，就点启用。
- 以后打 tag 会自动跑工作流。

工作流文件在：`/.github/workflows/release.yml`

---

## 4. 每次发新版本：创建 tag 触发自动编译

### 4.1 版本号规则

推荐：`vX.Y.Z`（比如 `v0.1.0`）。

### 4.2 用 GitHub Desktop 打 tag（简单）

GitHub Desktop 本身对 tag 操作不太直观，推荐用命令行只做打 tag：

1) 打开 PowerShell，进入项目目录：

```powershell
cd "C:\Users\Kevin\Music\Nostalgia-AI"
```

2) 创建 tag（示例 v0.1.0）：

```powershell
git tag v0.1.0
```

3) 推送 tag：

```powershell
git push origin v0.1.0
```

### 4.3 之后会发生什么

推送 tag 后：

1) GitHub Actions 自动开始运行
2) 自动编译 release APK
3) 自动创建 GitHub Release
4) Release 页面会出现 `app-release.apk`

你可以在：仓库 → `Actions` 查看日志。

你也可以在：仓库 → `Releases` 下载 APK。

---

## 4.4 如果你完全不想用命令行（纯网页/纯 Desktop）

现实情况：**创建 tag 最稳定的方式还是用命令行**（就两条命令）。

但如果你真的只想点点点：

1) 进入仓库网页 → `Releases`
2) 点 `Draft a new release`
3) 在 `Choose a tag` 输入 `v0.1.0`（新 tag）
4) 点 `Publish release`

> 注意：这种方式会创建 tag，但是否触发 workflow 取决于 GitHub 事件细节。
> 我推荐你还是按 4.2 的两条命令方式做（更稳）。

---

## 5. 常见问题（小白必看）

### 5.1 Actions 失败：提示签名变量为空

请检查你是否创建了这 4 个 secrets：

- `SIGNING_STORE_PASSWORD`
- `SIGNING_KEY_ALIAS`
- `SIGNING_KEY_PASSWORD`
- `SIGNING_KEYSTORE_BASE64`

### 5.2 我不想签名行不行？

可以，但 Release APK 不签名通常无法正常安装/更新。
本项目默认走签名流程。

### 5.3 我改了代码，怎么再上传？

#### GitHub Desktop

1) 看到改动列表
2) 填 Summary
3) Commit
4) Push

#### 命令行（完整一行）

```powershell
git add .; git commit -m "update"; git push
```

> 然后再打 tag 才会发 Release。

### 5.4 我想删除一个 tag 怎么办？（不建议新手操作）

如果你打错 tag，建议直接打一个新 tag（比如 v0.1.1）。

---

## 6. 工作流说明

工作流：`/.github/workflows/release.yml`

- 触发：push tag（`v*` 或 `*.*.*`）
- 使用：JDK 11
- 命令：`./gradlew :app:assembleRelease`
- 上传：`app/build/outputs/apk/release/app-release.apk`
