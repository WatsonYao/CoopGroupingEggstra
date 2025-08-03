source10.json 是出怪时间表。

怪物中英文对应关系如下：
    "SakelienShield" to "车",
    "SakelienCupTwins" to "垃圾桶",
    "Sakediver" to "鼹鼠",
    "Sakerocket" to "伞",
    "SakelienSnake" to "蛇",
    "SakelienTower" to "塔",
    "SakePillar" to "柱鱼",
    "SakeArtillery" to "铁球",
    "SakeDolphin" to "海豚",
    "SakelienBomber" to "绿帽",
    "SakeSaucer" to "锅盖",
    "SakelienGolden" to "金鲑鱼"

其中出怪规则如下：
共五个100秒的wave，简写为w1-w5,每个wave按照其对应的出怪时间出现，时间从100每秒减少到0。
其时间计算规则为 val showTime = floor(100.0 - time!! / 60.0).toInt() // 结果是 Int 类型，time 为json里面的Timing.

怪物里面，垃圾桶、铁球、塔为远程怪物。


## 实现完成
已创建timeline_visualization.html可视化分析，包含以下优化功能：

### 📊 可视化特点
- **时间线展示**: 每波100秒时间轴，高度加倍便于观察
- **颜色分类**: 红色(远程怪物)、蓝色(近身怪物)、灰色(小鲑鱼)
- **交互功能**: 鼠标悬停显示详细信息(出现时间、位置、原始Timing值)
- **重点展示**: 仅显示w3(p900)和w5(p1500)最高难度波次

### 🎯 优化策略核心
- **远程怪物优先**: 垃圾桶、铁球、塔必须优先处理
- **外出时机控制**: 外面怪物数量严格控制在4个以内
- **团队分工**: 专人处理远程怪物，其他人应对近身怪物
- **关键时段**: Wave 3密集远程怪物，Wave 5连续塔+垃圾桶最具挑战性

### 📋 具体实现细节
- 使用公式 `floor(100.0 - timing/60.0)` 精确计算出现时间
- 三层显示布局避免怪物重叠
- 针对p900和p1500难度的专门策略建议
- 响应式设计适配不同设备