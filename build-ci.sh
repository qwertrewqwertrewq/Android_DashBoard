#!/bin/bash
# 自动化构建和发布脚本

set -e

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}=== Android 自动构建脚本 ===${NC}"

# 清理 Gradle 锁
echo -e "${YELLOW}清理 Gradle 锁...${NC}"
rm -f ~/.gradle/wrapper/dists/gradle-*/*/gradle-*.zip.lck 2>/dev/null || true

# 构建 Debug
echo -e "${YELLOW}构建 Debug APK...${NC}"
./gradlew clean assembleDebug --no-daemon --stacktrace
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Debug APK 构建成功${NC}"
    DEBUG_APK=$(find app/build/outputs/apk/debug -name "*.apk" | head -1)
    echo -e "${GREEN}  路径: $DEBUG_APK${NC}"
else
    echo -e "${RED}✗ Debug APK 构建失败${NC}"
    exit 1
fi

# 构建 Release
echo -e "${YELLOW}构建 Release APK...${NC}"
./gradlew assembleRelease --no-daemon --stacktrace
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Release APK 构建成功${NC}"
    RELEASE_APK=$(find app/build/outputs/apk/release -name "*unsigned.apk" | head -1)
    echo -e "${GREEN}  路径: $RELEASE_APK${NC}"
else
    echo -e "${RED}✗ Release APK 构建失败${NC}"
    exit 1
fi

# 显示文件大小
echo -e "${YELLOW}输出文件信息:${NC}"
ls -lh app/build/outputs/apk/debug/*.apk app/build/outputs/apk/release/*.apk 2>/dev/null || true

echo -e "${GREEN}✓ 构建完成！${NC}"

# 提示后续操作
echo -e "${YELLOW}后续操作:${NC}"
echo "1. 本地安装测试: adb install -r '$DEBUG_APK'"
echo "2. 签名发布: 需要配置签名密钥"
echo "3. 推送到 GitHub: git push 将自动触发 GitHub Actions 构建"
echo "4. 发布版本: 创建 Git tag 将自动上传 Release"
