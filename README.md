# PVP Helper Mod

A multi-version Minecraft mod that provides PVP assistance features including player highlighting and projectile trajectory prediction.

## Supported Versions

- **Fabric 1.20.1** - Full feature support
- **Forge 1.8.9** - Player highlighting only

## Features

### 🎯 Player Highlighting (ESP/Wallhack)
- **See Through Walls**: View players through solid blocks with colored outlines
- **Dynamic Colors**: Outline color matches the player's leather helmet color
  - Wearing dyed leather helmet: Uses the helmet's color
  - No helmet or non-leather helmet: White outline
- **Toggle Control**: Hold `TAB` key to enable highlighting (Forge 1.8.9 uses a rebindable keybinding; toggle/hold and teammate filtering are configurable)

### 🚀 Projectile Trajectory Prediction (Fabric 1.20.1 Only)
**NEW FEATURE**: Automatically detect and predict projectile paths!

- **Supported Projectiles**:
  - Arrows (normal and spectral)
  - Tridents
  - Blaze fireballs (SmallFireballEntity)
  - Ghast fireballs (FireballEntity)

- **Visual Indicators**:
  - ✨ **White Glow**: Tracked projectiles show white outline
  - 📍 **Landing Markers**: 1x1x1 box outline at predicted landing point
    - 🔴 Red: Landing within configured range (default 20 blocks)
    - 🟡 Yellow: Landing beyond range (safe)
  - 📈 **Trajectory Line (optional)**: Predicted flight path line (toggleable)

- **Chat Alerts**: Real-time notifications with:
  - Projectile type (e.g., "Arrow", "Blaze Fireball")
  - Shooter name
  - Predicted landing coordinates
  - Distance warning if within configured range (default 20 blocks)

- **Performance Optimized**:
  - <100ms trajectory calculation
  - Max 50 concurrent projectiles tracked
  - GPU-friendly rendering (integrated graphics compatible)
  - FPS impact <5%

- **Physics Engine**:
  - Accurate gravity and air resistance simulation
  - Up to ~10 second prediction range (config-limited)
  - Collision detection with blocks

## Installation

### Prerequisites
- **Minecraft 1.20.1** (for Fabric) or **Minecraft 1.8.9** (for Forge)
- **Fabric Loader 0.15.0+** and **Fabric API 0.91.0+** (for Fabric version)
- **Forge 11.15.1.2318** (for Forge 1.8.9 version)

### Steps
1. Download the appropriate JAR from [Releases](https://github.com/colin1112a/pvp-helper/releases)
   - `playerhighlight-1.0.0.jar` for Fabric 1.20.1
   - `playerhighlight-forge-1.8.9-1.0.0.jar` for Forge 1.8.9
2. Place the JAR in your `.minecraft/mods` folder
3. Launch Minecraft

## Usage

### Player Highlighting
1. Join a multiplayer server or world with other players
2. **Hold TAB** to see player outlines through walls
3. Helmet colors automatically detected and applied

### Projectile Tracking (Fabric 1.20.1)
- **Automatic by default**: Optional toggles via Mod Menu (if installed) or `config/playerhighlight.properties`
- When arrows or fireballs are detected:
  - Projectile gets white glow outline
  - Landing point marked with colored box
  - Chat message shows prediction details
- Client command: `/bowstatus` shows each registered projectile type’s sample count and current learned physics params (gravity/drag); `/bowstatus reset <arrow|trident|fireball|all>` resets learning for that bucket.
- Client command: `/lookpvp` shows PvP details vs your most recent opponent (including the exact client-side distance when they hit you).
- Learned physics parameters are persisted to `config/playerhighlight-calibration.json` and automatically reloaded on next launch (schema-versioned; older data may be ignored after updates and will be re-learned).
- **Bow Preview (aim prediction)**: While drawing a bow, the mod predicts the arrow’s trajectory and landing point before you shoot (toggleable in Mod Menu).
- Bow preview now optionally simulates vanilla-like inaccuracy (random divergence) for a more realistic aim preview (toggle: `Bow Preview Inaccuracy` / `bowPreviewSimulateInaccuracy`).
- Projectile prediction can optionally simulate fluid slowdown for arrows when passing through water/lava (toggle: `Fluid Drag Simulation` / `simulateFluidDrag`, water drag key: `arrowWaterDrag`, default `0.6`).
- Learning sample quality: longer-distance shots have higher weight and update the learned params more strongly than very short shots.
- Learning sample filtering: only full-power bow arrows are used for learning (critical flag or near-max initial speed); mob-shot arrows are ignored; players who fire ≥5 arrows within 3 seconds are ignored for 10 minutes (learning only).

Example chat message:
```
[Projectile Alert] Arrow from Steve | Landing: (123.4, 64.0, -456.7) [NEARBY WARNING]
```

## Building from Source

### Requirements
- JDK 17 (for Fabric 1.20.1)
- JDK 8 (for Forge 1.8.9)

### Build Commands

**Fabric 1.20.1:**
```bash
./gradlew :fabric-1.20.1:build
```
Output: `fabric-1.20.1/build/libs/playerhighlight-1.0.0.jar`

**Forge 1.8.9:**
```bash
cd forge-1.8.9
gradle build
```
Output: `forge-1.8.9/build/libs/playerhighlight-forge-1.8.9-1.0.0.jar`

**Both versions:**
```bash
./gradlew build  # (Fabric only due to Gradle version conflict)
```

## Technical Details

### Architecture (Fabric 1.20.1)

**Player Highlighting:**
- `PlayerHighlightClient.java` - Main entry point, event registration
- `RenderHelper.java` - Color extraction from armor
- `PlayerEntityMixin.java` - Entity glow effect injection

**Projectile Tracking:**
- `ProjectileTrackerClient.java` - Main coordinator, entity detection
- `PhysicsSimulator.java` - Trajectory calculation engine
- `DynamicProjectileRegistry.java` - Per-type physics parameters
- `BowEnchantmentDetector.java` - Bow/crossbow type classification
- `TrajectoryRecorder.java` - Records real trajectory for calibration
- `CalibrationSystem.java` - Auto calibration from recorded trajectories
- `ProjectilePrediction.java` - Prediction result data class
- `TrackedProjectile.java` - Tracking state holder
- `ProjectileEntityMixin.java` - Projectile glow effect injection
- `LandingPointRenderer.java` - Landing marker rendering

### Performance Optimizations
- **Collision detection**: Raycasts every tick for accurate landing point
- **Trajectory sampling**: Store predicted points every 3 ticks
- **Early termination**: Only stop early when speed is very low and the projectile is close to the ground (avoids mid-air “landing”)
- **Concurrent data structures**: Lock-free reads with ConcurrentHashMap
- **Batch cleanup**: Remove expired projectiles every 20 ticks
- **Simple wireframe rendering**: Minimal GPU overhead

### Configuration

**User settings** (saved to `config/playerhighlight.properties`, configurable via Mod Menu if installed):
- Player highlighting on/off
- Projectile prediction on/off
- Trajectory line on/off
- Bow preview on/off
- Bow preview trajectory line on/off
- Bow preview landing marker on/off
- Bow preview inaccuracy simulation on/off
- Nearby warning range (blocks)
- Fluid drag simulation on/off
- Auto calibration on/off
- Debug mode on/off

**Hardcoded limits:**
- Max tracked projectiles: 50
- Max simulation time: 200 ticks (~10 seconds)
- Projectile expiry: 10 seconds
- Cleanup interval: 20 ticks (1 second)

## Known Limitations

- Projectile tracking only available in Fabric 1.20.1
- Forge 1.8.9 only supports player highlighting
- Maximum 50 projectiles tracked simultaneously
- Prediction accuracy ±2 blocks for fast projectiles
- Does not predict entity collisions (only block collisions)

## Development

### Project Structure
```
pvp-helper/
├── fabric-1.20.1/          # Fabric mod implementation
│   └── src/main/java/com/example/playerhighlight/
│       ├── PlayerHighlightClient.java
│       ├── RenderHelper.java
│       ├── ProjectileTrackerClient.java
│       ├── PhysicsSimulator.java
│       ├── LandingPointRenderer.java
│       └── mixin/
│           ├── PlayerEntityMixin.java
│           └── ProjectileEntityMixin.java
├── forge-1.8.9/            # Forge mod implementation
│   └── src/main/java/com/example/playerhighlight/
│       ├── PlayerHighlightMod.java
│       └── mixin/
│           └── MixinRenderManager.java
└── IMPLEMENTATION.md       # Detailed implementation log
```

### Technologies Used
- **Fabric API**: Client tick events, world rendering events
- **Mixin**: Bytecode injection for entity glow effects
- **Yarn Mappings**: Deobfuscation for Minecraft code
- **LWJGL/OpenGL**: Direct rendering for landing markers
- **GitHub Actions**: Automated CI/CD pipeline

## Contributing

Contributions welcome! Areas for improvement:
- [ ] Entity collision prediction
- [x] Configurable settings (GUI + config file)
- [x] Trajectory line visualization option
- [ ] Support for more Minecraft versions
- [ ] Ender pearl tracking
- [ ] Snowball/egg tracking

## License

MIT License - See LICENSE file for details

## Credits

Developed with assistance from Claude Code (Anthropic)

---

**⚠️ Disclaimer**: This mod provides PVP assistance features. Use responsibly and check server rules before use. Some servers prohibit ESP/trajectory prediction mods.
