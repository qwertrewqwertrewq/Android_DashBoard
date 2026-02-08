# GitHub Actions 自动构建配置指南

## 📋 功能说明

此项目已配置 GitHub Actions 自动构建流程，支持以下操作：

### 自动触发场景

1. **push 到主分支**
   - 在提交代码到 `main`、`master` 或 `develop` 分支时自动构建
   - 会生成 Debug 和 Release APK

2. **Pull Request**
   - 在创建 PR 到主分支时自动构建
   - 验证代码质量和构建完整性

3. **手动触发**
   - 在 GitHub Actions 页面手动运行工作流
   - 可选择构建类型

4. **创建 Git Tag**
   - 创建版本标签时自动构建并发布 Release
   - 自动上传 APK 到 GitHub Releases

## 🔧 工作流详解

工作流文件位置: `.github/workflows/android-build.yml`

### 构建环境
- 运行环境: Ubuntu Latest
- Java 版本: JDK 11
- 缓存: Gradle 缓存加速构建

### 输出产物
| 类型 | 路径 | 保留时间 |
|------|------|--------|
| Release APK | `app/build/outputs/apk/release/` | 30 天 |
| Debug APK | `app/build/outputs/apk/debug/` | 7 天 |
| Release 版本 | GitHub Releases | 永久 |

## 📦 发布流程

### 步骤 1: 本地测试

```bash
# 详细构建测试
./build-ci.sh

# 或手动构建
./gradlew clean assembleRelease
```

### 步骤 2: 提交并推送

```bash
git add .
git commit -m "Version 1.0.0"
git push origin main
```

### 步骤 3: GitHub 自动构建

- 在 GitHub 仓库 > Actions 标签页查看构建进度
- 构建完成后可以下载 artifacts

### 步骤 4: 创建发布版本（可选）

```bash
# 创建版本标签
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0
```

- GitHub Actions 检测到 tag 时自动创建 Release
- Release 包含完整的 APK 文件
- 可在 GitHub Releases 页面下载

## 🔑 配置认证

默认情况下使用 `GITHUB_TOKEN` 自动认证，无需额外配置。
该令牌自动由 GitHub Actions 提供，具有仓库访问权限。

## ✅ 检查构建状态

1. **在线查看**
   - 访问: GitHub 仓库 > Actions 标签页
   - 查看实时构建日志

2. **本地查看**
   - 检查 artifacts: 不同的 workflow run 会保存构建产物
   - APK 保留 7-30 天

## 📝 故障排除

### 构建失败
- 查看 Actions 日志获取详细信息
- 常见原因: 依赖问题、编译错误等
- 检查本地是否能成功构建

### APK 过大（175MB+）
- 原因: LibVLC 库较大
- 优化方案: 
  - 启用 ProGuard: `minifyEnabled true`
  - 按 CPU 架构拆分 APK

## 🚀 性能优化

1. **Gradle 缓存**
   - 工作流已配置缓存加速构建
   - 首次构建约 30-40 秒，后续 15-20 秒

2. **只构建 Release**
   - 修改 `.github/workflows/android-build.yml` 移除 Debug 构建

3. **条件构建**
   - 只在特定路径改动时构建
   - 可配置 `paths` 过滤器

## 📚 相关文件

- 工作流配置: `.github/workflows/android-build.yml`
- 本地构建脚本: `build-ci.sh`
- Gradle 配置: `build.gradle`
- 应用配置: `app/build.gradle`

## ⚙️ 常用命令

```bash
# 本地完整构建测试
./build-ci.sh

# 仅构建 Release
./gradlew assembleRelease

# 清理且构建
./gradlew clean assembleRelease

# 查看详细日志
./gradlew assembleRelease --stacktrace

# 查看 JVM 和 Gradle 信息
./gradlew -v
```

## 🎯 最佳实践

1. ✅ 在主分支上配置自动构建
2. ✅ 为发布版本创建 Git tags
3. ✅ 定期检查 Actions 日志
4. ✅ 保持依赖更新
5. ✅ 代码合并前验证 CI 通过

---

配置完成！之后每次 push 都会自动构建，创建 tag 时自动发布。
