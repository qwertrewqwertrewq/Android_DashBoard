# 签名和发布指南

## 📋 概述

此项目已配置自动构建流程，但要将应用发布到应用市场（Google Play、酷安等），需要用签名密钥对 APK 进行签名。

## 🔑 创建签名密钥

### 方法 1: 使用 Android Studio GUI（推荐）

1. 打开 Android Studio
2. Build → Generate Signed Bundle/APK
3. 选择 APK
4. 创建新的密钥库：
   - 文件名: `release-keystore.jks`
   - 密钥库密码: 设置强密码
   - 别名: `release-key` 
   - 有效期: 10000+ 天
5. 保存到 `app/` 目录（不要提交到 git）

### 方法 2: 使用命令行

```bash
keytool -genkey -v -keystore app/release-keystore.jks \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -alias release-key -storepass your_store_password \
    -keypass your_key_password \
    -dname "CN=Your Name, O=Your Company, C=CN"
```

参数说明：
- `-keystore`: 密钥库文件路径
- `-keyalg`: 加密算法（RSA）
- `-keysize`: 密钥大小（2048 位）
- `-validity`: 有效期（天数）
- `-alias`: 密钥别名
- `-dname`: 证书信息

## ⚠️ 密钥安全

### 本地构建配置

1. **创建 `local.properties`**（示例格式）：
```properties
# Firebase 配置
firebase.database.url=your_database_url

# 签名配置
KEYSTORE_FILE=release-keystore.jks
KEYSTORE_PASSWORD=your_store_password
KEY_ALIAS=release-key
KEY_PASSWORD=your_key_password
```

2. **更新 `app/build.gradle`**：
```gradle
android {
    ...
    signingConfigs {
        release {
            keyAlias = System.getenv('KEY_ALIAS') ?: project.properties['KEY_ALIAS']
            keyPassword = System.getenv('KEY_PASSWORD') ?: project.properties['KEY_PASSWORD']
            storeFile = file(System.getenv('KEYSTORE_FILE') ?: project.properties['KEYSTORE_FILE'])
            storePassword = System.getenv('KEYSTORE_PASSWORD') ?: project.properties['KEYSTORE_PASSWORD']
        }
    }
    
    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}
```

3. **本地构建命令**：
```bash
# 使用 local.properties 配置自动签名
./gradlew assembleRelease

# 或者直接传递参数
./gradlew assembleRelease \
  -DKEYSTORE_FILE=app/release-keystore.jks \
  -DKEYSTORE_PASSWORD=your_password \
  -DKEY_ALIAS=release-key \
  -DKEY_PASSWORD=your_password
```

### GitHub Actions 中的安全配置

1. **创建 GitHub Secrets**：
   - 访问 Settings → Secrets and variables → Actions
   - 创建以下 secrets:
     - `KEYSTORE_BASE64`: 密钥库文件的 Base64 编码
     - `KEYSTORE_PASSWORD`: 密钥库密码
     - `KEY_ALIAS`: 密钥别名
     - `KEY_PASSWORD`: 密钥密码

2. **编码密钥库文件**：
```bash
# Linux/Mac
base64 -i app/release-keystore.jks | pbcopy

# Windows PowerShell
[Convert]::ToBase64String((Get-Content -AsByteStream app/release-keystore.jks)) | Set-Clipboard
```

3. **工作流中使用 Secrets**：

编辑 `.github/workflows/build-and-release.yml`：

```yaml
# 构建前解码密钥库
- name: Decode Keystore
  run: |
    echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > app/release-keystore.jks
  
# 构建签名版本
- name: Build Signed Release APK
  run: |
    ./gradlew assembleRelease \
      -DKEYSTORE_FILE=app/release-keystore.jks \
      -DKEYSTORE_PASSWORD=${{ secrets.KEYSTORE_PASSWORD }} \
      -DKEY_ALIAS=${{ secrets.KEY_ALIAS }} \
      -DKEY_PASSWORD=${{ secrets.KEY_PASSWORD }}

# 清理密钥库文件
- name: Cleanup Keystore
  if: always()
  run: rm -f app/release-keystore.jks
```

## 📱 发布到应用市场

### Google Play Store

1. **创建开发者账户**
   - 访问 https://play.google.com/console
   - 注册并支付 $25 开发者费用

2. **创建应用**
   - 在 Google Play Console 中创建新应用
   - 填写应用信息

3. **上传 APK**
   - Build → All releases
   - 选择测试轨道（Alpha/Beta）先发布测试版
   - 上传签名的 APK

4. **完整信息**
   - 应用图标、截图
   - 应用描述、隐私政策
   - 内容分级问卷

### 酷安等国内应用市场

1. **注册开发者账户**
2. **上传 APK** 中文版本
3. **填写应用信息**
4. **等待审核**（通常 1-3 天）

## ✅ 验证签名

### 查看签名信息
```bash
# 查看签名证书指纹
keytool -list -v -keystore app/release-keystore.jks -alias release-key

# 输出示例：
# SHA1: AB:CD:EF:12:34:56:78:90:AB:CD:EF:12:34:56:78:90:AB:CD:EF
# SHA256: AB:CD:EF:12:34:56:78:90:AB:CD:EF:12:34:56:78:90:AB:CD:EF:12:34:56:78:90:AB:CD:EF:12:34
```

### 验证 APK 签名
```bash
# 使用 jarsigner 验证
jarsigner -verify -verbose app/build/outputs/apk/release/app-release.apk

# 使用 apksigner（Android SDK 提供）
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

## 🚀 自动发布工作流

典型的发布流程：

```bash
# 1. 开发并测试
git checkout -b feature/new-feature
# ... 代码修改 ...
git commit -am "Add new feature"

# 2. 合并到主分支
git checkout main
git merge feature/new-feature

# 3. 创建版本标签
git tag -a v1.0.1 -m "Release version 1.0.1"

# 4. 推送（自动触发 GitHub Actions）
git push origin main
git push origin v1.0.1

# 5. GitHub Actions 自动：
#    - 构建签名 APK
#    - 创建 Release
#    - 上传到 GitHub Releases
#    - 发送邮件通知

# 6. 手动发布到应用市场
#    - 从 GitHub Releases 下载 APK
#    - 上传到 Google Play / 其他应用市场
```

## 📝 常见问题

### Q: 忘记了密钥库密码怎么办？
**A:** 不幸的是无法恢复。需要生成新的密钥库，这意味着无法更新已发布的应用。

### Q: 密钥库可以在多个设备上使用吗？
**A:** 可以。建议将密码安全地存储，在需要的设备上配置。

### Q: 密钥库应该备份吗？
**A:** 强烈建议。将 `release-keystore.jks` 备份到安全位置（加密云存储或外部硬盘）。

### Q: GitHub Actions 中如何安全存储密封库？
**A:** 使用 Base64 编码存储在 GitHub Secrets，工作流执行时解码使用。

### Q: APK 大小过大怎么办？
**A:** 
1. 启用代码混淆：`minifyEnabled true`
2. 启用资源压缩
3. 按 CPU 架构拆分 APK
4. 移除未使用的库（LibVLC 较大）

## 🔍 签名检查清单

- [ ] 创建了签名密钥库
- [ ] 配置了 `app/build.gradle` 的 `signingConfig`
- [ ] 本地能成功构建签名 APK
- [ ] GitHub Secrets 已配置
- [ ] 工作流能成功构建签名版本
- [ ] 测试了签名 APK 的安装
- [ ] 备份了密钥库文件

---

准备好发布了！按照步骤进行即可。
