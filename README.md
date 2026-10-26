# 🎱 Open Pool Android

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Java 17](https://img.shields.io/badge/Language-Java%2017-007396?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![OpenGL ES](https://img.shields.io/badge/Graphics-OpenGL%20ES%202.0-5586A4?style=for-the-badge&logo=opengl&logoColor=white)](https://www.khronos.org/opengles/)
[![GitHub Release](https://img.shields.io/github/v/release/xan45/pooltoolandroid?include_prereleases&style=for-the-badge&logo=github&color=00E6A1)](https://github.com/xan45/pooltoolandroid/releases)
[![Build Status](https://img.shields.io/github/actions/workflow/status/xan45/pooltoolandroid/android-release.yml?style=for-the-badge&logo=github-actions&label=Release%20CI)](https://github.com/xan45/pooltoolandroid/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](LICENSE.txt)

An experimental, high-performance, physics-driven native 3D billiards simulation game for Android. Inspired by the scientific [pooltool](https://github.com/ekiefl/pooltool) billiards simulation engine, **Open Pool** delivers ultra-smooth 60/120 FPS gameplay, full cue ball spin and English dynamics, authentic acoustic audio via Android's `SoundPool`, realistic cushion jaws, and modern Material 3 glassmorphism UI.

---

## 🌟 Key Features

* **⚡ 120 Hz Fixed-Step Physics Engine**:
  * Deterministic Euler integration loop (`dt = 1/120s`) for precise physical reproducibility.
  * Real-time spherical impulse momentum resolution with positional overlap de-penetration.
  * Coulomb kinetic cloth sliding-friction and rolling-resistance deceleration.
* **🎯 Advanced Cue Ball Spin & English**:
  * **Follow (Top Spin)**: Forward angular momentum couples with the cloth; the cue ball surges forward following the target ball path.
  * **Draw (Back Spin)**: Reverse angular momentum bites into the cloth; the cue ball stops on collision and reverse-draws backward.
  * **English (Side Spin)**: Imparts angular velocity around the vertical axis, creating realistic rail deflection angles ("running" and "check" English).
  * **Cue Tip Squirt**: Simulates tip contact deflection when striking off-center.
  * **Visual 3D Cue Tip Alignment**: The 3D cue stick physically offsets its tip in world space to visually align with the chosen impact point.
* **🔊 High-Fidelity Acoustic Audio Engine**:
  * Native low-latency playback via Android's `SoundPool` (`AudioAttributes.USAGE_GAME`).
  * Authentic acoustic `.ogg` samples:
    * `cue_shot.ogg`: Crisp cue tip strike with power-scaled volume.
    * `ball_hit.ogg`: Phenolic resin collision with dynamic velocity volume scaling and randomized pitch micro-variations ($\pm 6\%$) to prevent acoustic fatigue.
    * `cushion.ogg`: Damped rubber cushion rail thuds.
    * `pocket.ogg`: Deep resonant pocket well drop.
* **📐 Realistic Cushion Jaws & Angle-Aware Pocket Ingress**:
  * 18 analytical collision segments: 6 main rail cushions plus 12 chamfered pocket jaws.
  * Angle-dependent acceptance cones ($\sim 65^\circ$ for corners, $\sim 55^\circ$ for side pockets).
  * Shallow or high-speed glancing shots rattle between the cushion jaws and bounce onto the table rather than being magnetically sucked into the hole.
  * Balls can be banked directly off the angled cushion facings into pockets.
* **🎨 Modern Material 3 UI & Glassmorphism**:
  * Floating glassmorphic HUD cards (`0xF00D221A`, `0xFF06120E`) with vibrant emerald (`#00E6A1`) and teal (`#0284C7`) accents.
  * Programmatic Android UI (zero XML layout overhead) keeping the APK footprint under **~6 MB**.
  * Complete edge-to-edge layout with `WindowInsetsCompat` and display cutout / notch compensation.
* **🎱 Interactive Cue Ball Spin Selector**:
  * Sleek interactive 2D cue ball modal view allowing players to drag the red chalk strike dot to any contact position.
  * Live percentage readouts (e.g. `Follow: 75% | Right English: 30%`) with quick "Reset Center (Stun)" preset.
* **✨ Target Impact Crosshair Indicator**:
  * Real-time raycasting collision predictor projecting a glowing emerald crosshair target ring (`#00E6A1`) at the predicted point of impact.
* **🪵 3D Cue Customization Shop**:
  * Equip 5 distinct customizable 3D cue stick models:
    * **Classic Wood**: Traditional polished maple shaft with smooth glide (`#C68A4C`)
    * **Ebony**: Dark obsidian wood with silver accent ferrule (`#1A1A1A`)
    * **Carbon Fiber**: Graphite composite for precision control (`#2D3748`)
    * **Gold Edition**: Luxury 24K polished brass cue stick (`#D4AF37`)
    * **Neon Blue**: Futuristic cyberpunk glowing cue shaft (`#00F0FF`)
  * Persistent storage via Android `SharedPreferences`.
* **🚀 Zero-GC Allocation Render Loop**:
  * Static geometry and dynamic scratch buffers are pre-allocated in native byte order (`FloatBuffer`), completely eliminating garbage collector pauses during gameplay.
* **🎥 Dual Camera Perspectives**:
  * **3D Perspective View**: Immersive player angle behind the table.
  * **2D Overhead View**: Top-down tactical table perspective for strategic shot planning.
* **👥 Game Modes**:
  * **Solo Practice Mode**: Continuous table training, bank shot practice, and break drills.
  * **Pass & Play Two-Player Mode**: Local turn-based match with dynamic player turn indicators and pocketed ball counters.

---

## ⬇️ Download Latest APK

Direct download from GitHub Releases:

👉 **[Download Latest Open Pool Pre-Release APK](https://github.com/xan45/pooltoolandroid/releases)**

Asset name format:
* `OpenPool-v0.4.0-beta.apk`

---

## 🎮 Game Controls

| Control | Input / Gesture | Action |
| :--- | :--- | :--- |
| **Aim Direction** | Drag finger across screen | Rotates the cue stick with 50% sensitivity reduction and smooth exponential moving average (EMA) interpolation. |
| **Shot Power & Pullback** | Drag finger backward along shot line | Pulls back the cue stick visually; release to strike the ball with power-scaled impulse. |
| **Cue Ball Spin & English** | Tap **Spin** button in HUD | Opens the interactive cue ball widget to adjust Top Follow, Back Draw, and Left/Right English. |
| **Camera View** | Tap **Camera** button | Toggles between 3D player perspective and 2D overhead top-down view. |
| **New Rack** | Tap **New Rack** button | Resets all 15 balls to regulation triangle formation with 8-ball centered in row 2 and apex ball on the foot spot. |
| **Cue Customization** | Tap **Cue Shop** button | Select and equip from 5 distinct 3D cue styles. |

---

## 🏛️ Architecture & System Design

```
+-----------------------------------------------------------------------------------+
|                                  MainActivity                                     |
|  - Material 3 Glassmorphism UI & Edge-to-Edge Cutout Inset Handling                |
|  - Native SoundPool Audio Engine (cue_shot, ball_hit, cushion, pocket)            |
|  - Interactive Cue Ball Spin Selector (Follow, Draw, English)                     |
|  - Persistent Preferences (Equipped Cue Model)                                    |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
|                            PoolSurface (GLSurfaceView)                            |
|             (Continuous Render Loop, Touch Gesture Routing, EGL Context)           |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
|                             PoolRenderer (Renderer)                               |
|  - 120 Hz Fixed-Step Physics Accumulator                                          |
|  - Spin & English Kinematics (Follow / Draw / Rail English / Squirt)              |
|  - 18 Analytical Cushion & Pocket Jaw Segments (Chamfer Rebounds)                 |
|  - Angle-Aware Pocket Acceptance Cones                                            |
|  - Zero-GC Allocation OpenGL ES 2.0 Geometry Buffers                             |
|  - Real-time Raycast Collision Impact Target Ring                                 |
+-----------------------------------------------------------------------------------+
```

---

## 🛠️ Build Instructions

### Prerequisites
* **Android SDK**: `compileSdk = 37`, `minSdk = 26` (Android 8.0 Oreo or higher), `targetSdk = 35`
* **JDK**: Java 17 or higher
* **Gradle**: Gradle 8.8+ (Gradle wrapper included)

### Opening in Android Studio
1. Launch **Android Studio**.
2. Select **Open** and choose the root repository folder (`pooltoolandroid`).
3. Android Studio automatically detects the root Gradle configuration and syncs the `:app` module.

### Building via Terminal

**From repository root:**

*Linux / macOS:*
```bash
./gradlew assembleDebug
```

*Windows (PowerShell / CMD):*
```powershell
.\gradlew.bat assembleDebug
```

**From `android/` subfolder:**

```bash
cd android
./gradlew assembleDebug
```

The output APK will be located at:
`android/app/build/outputs/apk/debug/app-debug.apk`

---

## 🚦 Roadmap

- [x] OpenGL ES 2.0 3D Rendering Engine
- [x] Zero-GC Memory Optimization (Rock-solid 60/120 FPS)
- [x] Material 3 Glassmorphic Game UI & Floating Header
- [x] 3D Cue Customization System (5 Distinct Cue Materials)
- [x] Raycast Collision Target Ring Indicator
- [x] Low-Latency Native Audio Engine (`SoundPool` with `.ogg` Billiard Samples)
- [x] Full Cue Ball Spin & English (Follow, Draw, Side English, Squirt)
- [x] Interactive Cue Ball Spin Selector Modal
- [x] 12 Chamfered Cushion Jaws & Directional Pocket Acceptance Cones
- [x] Root & Subfolder Gradle Workspace Support for Android Studio
- [x] Automated GitHub Actions APK Release Workflow
- [ ] Online Multiplayer & Matchmaking
- [ ] Spin & English Ball Visual Surface Rotation Texture
- [ ] Table Felt Color & Lighting Customization

---

## 👨‍💻 Credits & Maintainer

* **Developer & Maintainer**: **Abhinav Kishore ([@xan45](https://github.com/xan45))**
* **Repository**: [xan45/pooltoolandroid](https://github.com/xan45/pooltoolandroid)
* **Inspiration & Scientific Reference**: Evan Kiefl et al., *Pooltool: A Python package for realistic billiards simulation* (JOSS 2024).

---

## 📄 License

This project is licensed under the [MIT License](LICENSE.txt).
Contributions, bug reports, and pull requests are warmly welcomed!
