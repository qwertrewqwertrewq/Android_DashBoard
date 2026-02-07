# 家庭面板 App

一个适用于**老旧安卓设备**的智能家居控制面板应用，将闲置平板改造成家庭控制中心。

## 应用场景

将老旧的 Android 平板（如 Android 7.1 设备）改造成：
- 实时监控画面显示器
- 智能家居控制面板
- 智能节能显示屏

## 核心功能

### 1. RTSP 实时视频流
- 支持 RTSP 协议视频流（H265 编码）
- 自动 90° 旋转（16:9 横屏 → 9:16 竖屏）
- 使用 LibVLC 播放，支持硬件加速
- 自动重连机制（网络异常自动恢复）
- 优化内存占用，适配低端设备

### 2. 可配置控制按钮
- 8 个可自定义按钮（7 个功能按钮 + 1 个配置按钮）
- 不同颜色区分，28sp 大字体（适合远距离查看）
- 通过 **curl 命令**执行 HTTP 请求
- 支持智能家居设备控制（灯光、开关、传感器等）
- 配置持久化保存

### 3. 智能亮度管理
- 30 秒无操作自动降低屏幕亮度（节能）
- 触摸屏幕自动恢复亮度
- 每 30 秒周期性强制降低亮度（容错机制）
- 防止意外恢复全亮状态
- 使用 Root 权限直接控制背光

### 4. 系统优化
- 前台服务保活（防止被系统杀死）
- WakeLock 保持 CPU 运行
- 低内存占用设计（约 290MB）
- 适配 1GB RAM 设备

## 技术架构

### 核心组件

#### RtspStreamManager.kt
RTSP 视频流管理器：
- LibVLC 播放器封装
- 网络缓冲优化（2000ms）
- 硬件解码加速（mediacodec）
- 自动重连机制（指数退避）
- 事件监听与日志记录

#### ScreenControl.kt
屏幕亮度控制：
- Root Shell 背光控制
- 自动查找背光路径
- 强制执行模式（忽略状态检查）
- 最大/最低亮度管理

#### ConfigManager.kt
配置管理：
- SharedPreferences 持久化
- RTSP URL 配置
- 按钮名称和 curl 命令配置

#### CurlExecutor.kt
Curl 命令解析执行器：
- 解析 curl 命令参数（-X, -H, -d）
- OkHttp 执行 HTTP 请求
- JSON 响应格式化输出

#### MainActivity.kt
主界面协调器：
- 视频显示与旋转
- 触摸事件处理
- 定时器管理
- 配置对话框

## 系统要求

- **Android 版本**: 7.1+ (API Level 25+)
- **Root 权限**: 必需（用于控制屏幕亮度）
- **内存**: 建议 1GB+ RAM
- **架构**: ARM/ARM64

## 快速开始

### 1. 构建应用

```bash
./gradlew assembleDebug
```

### 2. 安装到设备

```bash
./gradlew installDebug
```

### 3. 配置使用

1. **授予 Root 权限**：应用启动时会请求 Root 权限
2. **配置 RTSP 地址**：点击"编辑"按钮，输入 RTSP URL（如 `rtsp://192.168.31.17:8554/stream1`）
3. **配置控制按钮**：为每个按钮设置名称和 curl 命令
4. **开始使用**：视频自动播放，点击按钮控制设备

### 配置示例

**RTSP URL 示例：**
```
rtsp://192.168.31.17:8554/stream1
```

**Curl 命令示例：**
```bash
# 打开灯光
curl -X POST http://192.168.1.100/api/light/on

# 查询温度
curl -X GET http://192.168.1.101/api/temperature

# JSON 请求
curl -X POST http://192.168.1.102/api/control \
  -H "Content-Type: application/json" \
  -d '{"device":"switch","action":"toggle"}'
```

## 依赖库

- **LibVLC**: 3.4.4 - RTSP 视频播放
- **OkHttp**: 4.9.0 - HTTP 请求
- **Kotlin Coroutines**: 异步处理

## 配置说明

### 视频参数
- 默认缓冲：2000ms（可调整以平衡流畅度和延迟）
- 硬件加速：mediacodec_ndk, mediacodec_jni
- 自动重连：最多 5 次，指数退避（2s → 10s）

### 亮度控制
- 无操作延迟：30 秒
- 容错周期：30 秒
- 最低亮度：1（避免完全黑屏）

## 注意事项

**必要条件：**
- 设备必须已获取 Root 权限
- 已安装 SuperSU、Magisk 等 Root 管理工具
- 授予应用 Root 权限和摄像头权限

**最佳实践：**
- 使用局域网 RTSP 源以降低延迟
- 根据设备性能调整视频缓冲大小
- 定期重启应用以释放内存
- 建议使用有线网络连接

**节能建议：**
- 应用会自动降低亮度节省电量
- 建议插电使用作为固定面板
- 周期性容错机制防止亮度异常恢复

## 故障排查

**视频不显示：**
- 检查 RTSP URL 是否正确
- 确认网络连接正常
- 查看 logcat 日志：`adb logcat | grep RtspStreamManager`

**按钮无响应：**
- 检查 curl 命令格式是否正确
- 确认目标设备网络可达
- 查看日志中的错误信息

**亮度控制失效：**
- 确认已授予 Root 权限
- 检查背光路径：`adb logcat | grep ScreenControl`
- 查看是否找到背光设备

## 许可证

本项目仅供学习和个人使用。

## 贡献

欢迎提交 Issue 和 Pull Request！

## 联系方式

如有问题或建议，请通过 Issue 反馈。

## 安装应用

```bash
./gradlew installDebug
```
