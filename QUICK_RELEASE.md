# 🚀 快速发布指南

## 即刻开始

### 1️⃣ 本地完整构建

```bash
# 在项目根目录执行
./build-ci.sh
```

输出：
- ✅ Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- ✅ Release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`

### 2️⃣ 推送代码到 GitHub

```bash
git add .
git commit -m "Update project"
git push origin main
```

→ GitHub Actions **自动构建**（5-10 分钟）

### 3️⃣ 创建发布版本（可选）

```bash
# 创建版本标签
git tag -a v1.0.0 -m "Release 1.0.0"
git push origin v1.0.0
```

→ GitHub Actions **自动发布到 GitHub Releases**

### 4️⃣ 查看构建结果

**GitHub 网页查看**：
- Repository → Actions → 查看最新的 workflow run
- 或 Releases → 查看已发布版本

**下载 APK**：
- GitHub Actions artifacts（7-30 天）
- GitHub Releases（永久保存）

## 📊 工作流对应关系

| 触发事件 | 工作流文件 | 构建类型 | 输出位置 |
|---------|----------|--------|--------|
| push 到 main | `android-build.yml` | Debug + Release | Artifacts |
| Pull Request | `android-build.yml` | Debug + Release | Artifacts |
| 创建 Git tag | `build-and-release.yml` | Release | GitHub Releases |
| 手动运行 | `build-and-release.yml` | Release | GitHub Releases |

## 🎯 常用命令速查

### 本地构建
```bash
# 完整构建（推荐）
./build-ci.sh

# 仅构建 Release
./gradlew assembleRelease

# 仅构建 Debug
./gradlew assembleDebug

# 详细日志
./gradlew assembleRelease --stacktrace
```

### Git 版本管理
```bash
# 查看所有标签
git tag -l

# 创建标签
git tag -a v1.0.0 -m "Version 1.0.0"

# 推送标签
git push origin v1.0.0

# 删除本地标签
git tag -d v1.0.0

# 删除远程标签
git push origin --delete v1.0.0
```

### GitHub Actions 查看
```bash
# 查看最新的 workflow runs（需要 GitHub CLI）
gh run list --repo owner/repo

# 查看特定 run 的日志
gh run view RUN_ID --repo owner/repo
```

## 📱 安装到设备

### 前提条件
- 设备已连接到 PC（USB 或 ADB over Wi-Fi）
- 已安装 ADB

### 安装 Debug 版本
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 安装 Release 版本
```bash
adb install -r app/build/outputs/apk/release/app-release-unsigned.apk
```

### 查看设备
```bash
# 查看已连接的设备
adb devices

# 移除设备
adb disconnect

# 通过 Wi-Fi 连接
adb connect 192.168.1.100:5555
```

## 🔐 签名配置（可选但推荐）

需要发布到应用市场时配置签名。详见 [SIGNING_AND_RELEASE.md](SIGNING_AND_RELEASE.md)

快速步骤：
1. 创建签名密钥库
2. 配置 GitHub Secrets
3. 修改工作流添加签名步骤

## 📈 构建质量检查

在发布前验证：

```bash
# 查看 APK 文件大小
ls -lh app/build/outputs/apk/*/

# 验证 APK 完整性
file app/build/outputs/apk/release/app-release-unsigned.apk

# 查看 APK 内容（需要 zip 工具）
unzip -l app/build/outputs/apk/release/app-release-unsigned.apk | head -20
```

## 🆘 故障排除

### 构建失败

1. **查看详细日志**
   ```bash
   ./gradlew assembleRelease --stacktrace
   ```

2. **清理缓存后重试**
   ```bash
   ./gradlew clean
   ./gradlew assembleRelease
   ```

3. **检查 Java 版本**
   ```bash
   java -version
   # 应该是 11.x 或更高
   ```

### GitHub Actions 失败

1. 访问 Actions → 最新 run → 查看失败的步骤
2. 检查是否有缺失的依赖或配置
3. 本地重现相同的构建

### APK 过大（>100MB）

- 启用 ProGuard 混淆
- 启用资源压缩
- 按 CPU 架构拆分 APK

## 📋 发布检查清单

发布前确保：

- [ ] 本地构建成功
- [ ] 所有测试通过
- [ ] 版本号已更新
- [ ] 更新日志已准备
- [ ] Git tag 已创建
- [ ] GitHub Actions 构建成功
- [ ] APK 文件符合预期大小
- [ ] 测试安装到设备成功

## 📞 获取帮助

- 查看 [CI_CD_GUIDE.md](CI_CD_GUIDE.md) - 完整 CI/CD 配置说明
- 查看 [SIGNING_AND_RELEASE.md](SIGNING_AND_RELEASE.md) - 签名和发布详说
- 查看 [README.md](README.md) - 项目说明

---

**Ready to deploy?** 👉 执行 `./build-ci.sh` 开始吧！
