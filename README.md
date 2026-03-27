# 🔋 电池健康检测 (Battery Health Tracker)

一款 Android 原生应用，通过记录充电数据来估算电池实际容量，判断电池健康状态。

## 技术栈

- **语言**: Kotlin
- **UI 框架**: Jetpack Compose + Material 3
- **架构**: MVVM (ViewModel + Repository)
- **数据库**: Room (SQLite)
- **最低 SDK**: API 26 (Android 8.0)
- **目标 SDK**: API 35 (Android 15)

## 项目结构

```
app/src/main/java/com/batteryhealth/app/
├── MainActivity.kt                    # 主入口，底部导航
├── data/
│   ├── model/
│   │   └── ChargingRecord.kt         # 数据模型 + 健康度评级
│   └── repository/
│       ├── BatteryDatabase.kt        # Room 数据库 + DAO
│       └── BatteryRepository.kt      # 数据操作 + 容量算法
├── viewmodel/
│   └── BatteryViewModel.kt          # UI 状态管理
└── ui/
    ├── theme/
    │   └── Theme.kt                  # 深色主题配色
    └── screens/
        ├── HomeScreen.kt             # 概览：仪表盘 + 健康建议
        ├── RecordScreen.kt           # 记录：充电数据录入
        ├── HistoryScreen.kt          # 历史：记录列表
        └── TrendScreen.kt            # 趋势：折线图 + 统计
```

## 功能特性

### 📊 概览页
- 半圆仪表盘动态显示健康度百分比
- 颜色随健康状态变化（绿/黄/红）
- 估算容量、记录次数、监测天数统计
- 个性化健康建议（5 个等级）

### ⚡ 记录页
- 输入标称容量、充电前后电量、充电时长
- 可选输入充电功率（提升计算精度）
- 实时表单验证与错误提示

### 📋 历史页
- 所有充电记录列表（新到旧）
- 每条记录显示健康度徽章
- 滑动确认删除

### 📈 趋势页
- 原生 Canvas 绘制健康度折线图（带曲线平滑）
- 最高/最低/平均容量统计
- 健康度分布横向进度条

## 计算原理

```
估算容量 = 充电量(%) × 标称容量 ÷ 充电效率系数

充电效率系数：
  - 充电量 > 70%：0.85（高电量段效率低）
  - 充电量 < 20%：0.90（低电量段效率高）
  - 其他区间：0.88

若提供充电功率：
  功率法容量 = (功率W × 时长h) ÷ 标称电压(3.85V) × 1000 ÷ 传输效率(0.82)
  最终 = 百分比法 × 60% + 功率法 × 40%

健康度 = 估算容量 ÷ 标称容量 × 100%（加权平均多次记录）
```

## 健康等级

| 健康度 | 等级 | 说明 |
|--------|------|------|
| ≥ 90%  | 🌟 优秀 | 电池状态极佳 |
| 80–89% | ✅ 良好 | 续航接近新机 |
| 70–79% | ⚠️ 一般 | 续航缩短约 20% |
| 60–69% | 🔶 较差 | 建议考虑换电池 |
| < 60%  | 🔴 严重老化 | 强烈建议换电池 |

## 开发环境要求

- Android Studio Ladybug (2024.2.1) 或更高
- JDK 11+
- Android SDK API 35

## 构建步骤

1. 用 Android Studio 打开项目根目录
2. 等待 Gradle 同步完成
3. 修改 `local.properties` 中的 `sdk.dir` 为你的 SDK 路径
4. 点击 Run 或执行 `./gradlew assembleDebug`

## APK 路径

构建完成后 APK 位于：
```
app/build/outputs/apk/debug/app-debug.apk
```
