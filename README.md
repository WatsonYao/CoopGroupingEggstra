# CoopGroupingEggstra (Android)

## 项目简介

这是一个用于查看 Eggstra Work 出怪顺序的 Android 应用。  
新主页为 `HomeActivity`（启动页），旧主页 `TeamActivity` 已保留但不再作为启动入口。

## 主要功能

1. 新主页三栏时间轴
- 中间主区按 `A / B / C` 三列展示出怪。
- 时间轴范围为 `100s -> 25s`，每秒一行。
- 每行固定高度 `18dp`。
- 同一秒同一列出现多个怪物时，横向并排显示。
- 仅展示 Boss（不展示 `Lesser=true` 的小怪）。

2. 左侧侧边栏
- 支持选择 `event01` 到 `event12`。
- `Settings` 按钮：打开设置页。
- `About` 按钮：打开关于对话框，列出 12 个 event 网页链接并支持跳转浏览器。

3. 右侧侧边栏
- 显示当前 event 的 Wave + 难度列表。
- 当前逻辑为：每个 wave 的所有可用难度全部显示（不再只显示前两个）。
- 排序规则：先按 `W1 -> W5`，再按难度百分比升序。

4. 关卡过滤
- 当前纳入显示的 wave 编码：`0/1/2/5/6/7/10`。
- 含普通潮（Normal）、雾天（Fog）以及 `Cohock Charge`。

5. 设置页（黑色主题）
- 每种 Boss 可自定义显示颜色。
- 颜色修改后左侧标签背景立即更新，主时间轴立即刷新。
- 支持重命名 Boss 显示名（不能为空）。
- 颜色与名称都通过 `SharedPreferences` 持久化。

6. 状态记忆
- 自动记住上次选择的 event 与 wave 难度项，重启后恢复。

## 数据来源与文件说明

1. 原始数据（JS）
- 目录：`raw/`
- 文件：`EggstraWork01.js` ~ `EggstraWork12.js`
- 来源网页示例：
  - `https://leanny.github.io/eggstra_work/coop_event_01.html`
  - `https://leanny.github.io/eggstra_work/coop_event_12.html`

2. 应用运行数据（JSON）
- 目录：`app/src/main/res/raw/`
- 文件：`eggstrawork01.json` ~ `eggstrawork12.json`
- 主页读取的是以上 `eggstraworkXX.json`，不是旧的 `sourceXX.json`。

## About 页面跳转地址

应用内 About 对话框包含以下 12 个链接（点击后使用系统浏览器打开）：

1. `https://leanny.github.io/eggstra_work/coop_event_01.html`
2. `https://leanny.github.io/eggstra_work/coop_event_02.html`
3. `https://leanny.github.io/eggstra_work/coop_event_03.html`
4. `https://leanny.github.io/eggstra_work/coop_event_04.html`
5. `https://leanny.github.io/eggstra_work/coop_event_05.html`
6. `https://leanny.github.io/eggstra_work/coop_event_06.html`
7. `https://leanny.github.io/eggstra_work/coop_event_07.html`
8. `https://leanny.github.io/eggstra_work/coop_event_08.html`
9. `https://leanny.github.io/eggstra_work/coop_event_09.html`
10. `https://leanny.github.io/eggstra_work/coop_event_10.html`
11. `https://leanny.github.io/eggstra_work/coop_event_11.html`
12. `https://leanny.github.io/eggstra_work/coop_event_12.html`

## 构建与安装

1. 构建 Debug APK

```bash
./gradlew :app:assembleDebug
```

2. 构建 Release APK

```bash
./gradlew :app:assembleRelease
```

3. APK 下载/获取地址（之前的下载地址）
- 目录：`app/release/`
- 常见文件名：`app-release.apk`
