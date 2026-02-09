# PVP Helper Mod

**注意：本仓库由codex与claude共同构建完成。代码质量不做保障。本地已验证可用**

[English](#english) | [中文](#中文)

---

## English

A Minecraft Fabric 1.20.1 client-side mod that provides PVP assistance features including player highlighting and projectile trajectory prediction.

### Features

**Player Highlighting**
- See players through walls with colored outlines (hold TAB)
- Outline color matches the player's leather helmet dye color
- White outline for players without dyed leather helmets

**Projectile Trajectory Prediction**
- Supported projectiles: arrows, spectral arrows, tridents, blaze fireballs
- White glow outline on tracked projectiles
- Landing point markers (red = nearby, yellow = safe)
- Optional trajectory line visualization
- Chat alerts with projectile type, shooter name, and landing coordinates

**Bow Preview**
- Predicts arrow trajectory while drawing a bow or crossbow
- Shows landing point marker before you shoot
- Optional inaccuracy simulation for realistic preview

**Auto-Calibration**
- Learns physics parameters (gravity, drag) from actual arrow trajectories
- Closed-form least-squares estimation with EMA smoothing
- Outlier rejection when model is mature (100+ samples)
- Learned parameters persist across sessions

### Installation

**Requirements:**
- Minecraft 1.20.1
- Fabric Loader 0.15.0+
- Fabric API 0.91.0+
- [Mod Menu](https://modrinth.com/mod/modmenu) (optional, for GUI settings)

**Steps:**
1. Download `playerhighlight-1.0.0.jar` from [Releases](https://github.com/colin1112a/pvp-helper/releases)
2. Place it in your `.minecraft/mods` folder
3. Launch Minecraft with Fabric

### Commands

| Command | Description |
|---------|-------------|
| `/bowstatus` | Show learned physics parameters for each projectile type |
| `/bowstatus reset <type>` | Reset learning for a type (`arrow`, `trident`, `fireball`, `all`) |
| `/lookpvp` | Show PVP stats vs your most recent opponent |

### Configuration

All settings are configurable via Mod Menu or `config/playerhighlight.properties`:

- Player highlighting on/off
- Projectile prediction on/off
- Trajectory line on/off
- Bow preview on/off (trajectory, landing marker, inaccuracy)
- Nearby warning range (default 20 blocks)
- Fluid drag simulation on/off
- Auto calibration on/off
- Debug mode on/off

Learned calibration data is saved to `config/playerhighlight-calibration.json`.

### Building from Source

```bash
./gradlew build
```

Output: `build/libs/playerhighlight-1.0.0.jar`

Requires JDK 17.

### License

MIT License - See [LICENSE](LICENSE) file for details.

---

## 中文

Minecraft Fabric 1.20.1 客户端 Mod，提供 PVP 辅助功能，包括玩家透视高亮和弹射物轨迹预测。

### 功能

**玩家高亮**
- 按住 TAB 键透过方块看到玩家轮廓
- 轮廓颜色匹配玩家皮革头盔的染色
- 无染色皮革头盔的玩家显示白色轮廓

**弹射物轨迹预测**
- 支持：箭矢、光灵箭、三叉戟、烈焰弹
- 被追踪的弹射物显示白色发光轮廓
- 落点标记（红色 = 近距离警告，黄色 = 安全）
- 可选轨迹线显示
- 聊天栏提示弹射物类型、射手名称和预测落点坐标

**弓箭预瞄**
- 拉弓/弩时实时预测箭矢轨迹和落点
- 射击前显示落点标记
- 可选散布模拟，更贴近实际效果

**自动校准**
- 从实际箭矢轨迹自动学习物理参数（重力、空气阻力）
- 闭式最小二乘估计 + EMA 指数平滑
- 模型成熟后（100+ 样本）自动拒绝异常样本
- 学习数据跨会话持久化保存

### 安装

**前置要求：**
- Minecraft 1.20.1
- Fabric Loader 0.15.0+
- Fabric API 0.91.0+
- [Mod Menu](https://modrinth.com/mod/modmenu)（可选，用于图形化设置）

**步骤：**
1. 从 [Releases](https://github.com/colin1112a/pvp-helper/releases) 下载 `playerhighlight-1.0.0.jar`
2. 放入 `.minecraft/mods` 文件夹
3. 使用 Fabric 启动 Minecraft

### 命令

| 命令 | 说明 |
|------|------|
| `/bowstatus` | 查看各弹射物类型的学习参数 |
| `/bowstatus reset <类型>` | 重置指定类型的学习数据（`arrow`、`trident`、`fireball`、`all`） |
| `/lookpvp` | 查看与最近对手的 PVP 统计 |

### 配置

所有设置可通过 Mod Menu 或 `config/playerhighlight.properties` 修改：

- 玩家高亮 开/关
- 弹射物预测 开/关
- 轨迹线 开/关
- 弓箭预瞄 开/关（轨迹线、落点标记、散布模拟）
- 近距离警告范围（默认 20 格）
- 流体阻力模拟 开/关
- 自动校准 开/关
- 调试模式 开/关

校准数据保存在 `config/playerhighlight-calibration.json`。

### 从源码构建

```bash
./gradlew build
```

输出：`build/libs/playerhighlight-1.0.0.jar`

需要 JDK 17。

### 许可证

MIT License - 详见 [LICENSE](LICENSE) 文件。
