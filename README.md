# Open Pool Android Port

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Java/Kotlin](https://img.shields.io/badge/Language-Java%20%2F%20Kotlin-007396?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![GitHub Release](https://img.shields.io/github/v/release/xan45/pooltoolandroid?include_prereleases&style=for-the-badge&logo=github&color=00E6A1)](https://github.com/xan45/pooltoolandroid/releases)
[![Build Status](https://img.shields.io/github/actions/workflow/status/xan45/pooltoolandroid/android-release.yml?style=for-the-badge&logo=github-actions&label=Release%20CI)](https://github.com/xan45/pooltoolandroid/actions)
[![License](https://img.shields.io/github/license/xan45/pooltoolandroid?style=for-the-badge&color=blue)](LICENSE.txt)
[![Last Commit](https://img.shields.io/github/last-commit/xan45/pooltoolandroid?style=for-the-badge&color=orange)](https://github.com/xan45/pooltoolandroid/commits/main)

An experimental, high-performance native 3D pool and billiards simulation game for Android, featuring modern Material 3 design, custom 3D cue sticks, precise touch aiming, and automated CI/CD releases.

---

## 🌟 Key Features

* **Physics-Based Gameplay**: Fixed-step 120 Hz deterministic physics engine supporting realistic ball-to-ball collisions, cushion rebounds, friction deceleration, and pocket drop detection.
* **Modern Material 3 UI**: Glassmorphic dark theme, elevated cards, interactive rounded buttons, press-scale animations, and native ripple touch feedback.
* **Touch Aiming & Power Control**: Precise finger aiming with 50% sensitivity reduction, Exponential Moving Average (EMA) angle smoothing, raycast collision preview, and dynamic cue stick pullback shot power scaling.
* **3D Cue Customization**: Equip 5 distinct custom 3D cue stick models (*Classic Wood, Ebony, Carbon Fiber, Gold Edition, Neon Blue*) with persistent storage.
* **Realistic Table Geometry**: Dark green tournament cloth (`#0B5D3B`), 6 tapered cloth cushions (`#094B30`) with 45° chamfers, 3D recessed pocket wells (`#080A0A`), mahogany frame, and diamond sight markers.
* **Target Impact Indicator**: Replaced duplicate white ghost balls with a sleek glowing emerald crosshair target ring (`#00E6A1`) at predicted impact locations.
* **Automated CI/CD Pipeline**: GitHub Actions workflow automatically builds signed APKs, generates releases, tags semantic versions (`OpenPool-vX.Y.Z-beta.apk`), and attaches changelogs.

---

## 📱 Screenshots

> *(Game HUD, Cue Selection Shop, Material 3 Main Menu, and 3D Table Rendering)*

---

## ⬇️ Download Latest APK

Get the latest release APK directly from the GitHub Releases page:

👉 **[Download Open Pool Pre-Release APK](https://github.com/xan45/pooltoolandroid/releases)**

Sample release asset format:
* `OpenPool-v0.3.0-beta.apk`

---

## 🛠️ Build Instructions

### Requirements
* **Android SDK**: `compileSdk = 37`, `minSdk = 26` (Android 8.0 Oreo or higher), `targetSdk = 35`
* **JDK**: Java 17 or higher
* **Gradle**: Gradle 8.8+ (Gradle Wrapper included)

### Building via Command Line

Clone the repository and build the debug/release APK:

```bash
git clone https://github.com/xan45/pooltoolandroid.git
cd pooltoolandroid/android
```

On Linux/macOS:
```bash
./gradlew assembleDebug
```

On Windows (PowerShell/CMD):
```cmd
.\gradlew.bat assembleDebug
```

The compiled APK will be generated at:
`android/app/build/outputs/apk/debug/app-debug.apk`

---

## 🎮 Game Controls

| Control Action | Gesture / Input | Description |
| :--- | :--- | :--- |
| **Aim Direction** | Drag finger across screen | Rotates the cue stick with 50% sensitivity scaling and smooth LERP interpolation. |
| **Pullback Shot Power** | Drag backward along cue line | Pulls back the cue stick visually; release to strike the ball with power scaling. |
| **Camera View** | Tap **Camera** button | Toggles between 3D player perspective camera and overhead 2D tactical view. |
| **New Rack** | Tap **New Rack** button | Resets all balls to initial 15-ball triangle rack position. |
| **Cue Shop** | Tap **Cue Shop** button | Opens cue customization shop to select and equip 3D cue styles. |

---

## 🏛️ Architecture & Technical Details

```
+-------------------------------------------------------------------------+
|                              MainActivity                               |
|       (Material 3 UI, SharedPreferences, ToneGenerator Sound Engine)     |
+-------------------------------------------------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------------+
|                       PoolSurface (GLSurfaceView)                       |
|           (OpenGL ES 2.0 Context, Continuous Render Loop)               |
+-------------------------------------------------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------------+
|                        PoolRenderer (Renderer)                          |
|  - 120 Hz Fixed-Step Physics Accumulator                                |
|  - Zero-GC Allocation Render Loop (Pre-compiled Static VBOs)             |
|  - Real-time Raycast Collision Target Ring                              |
|  - Customizable 3D Cue Stick Materials                                  |
+-------------------------------------------------------------------------+
```

---

## 🚦 Development Status & Roadmap

- [x] OpenGL ES 2.0 3D Rendering Engine
- [x] Material 3 Mobile Game UI & Glassmorphism Header
- [x] Zero-GC Memory Optimization (Rock-solid 60/120 FPS)
- [x] 6 Tapered Cloth Cushions & Recessed 3D Pocket Wells
- [x] Dynamic Cue Customization System (5 Cue Styles)
- [x] Raycast Impact Crosshair Indicator
- [x] Automated GitHub Actions APK Build & Release Workflow
- [ ] Spin & English Ball Strike Offset
- [ ] Online Multiplayer & Leaderboards
- [ ] Sound Effects Expansion

---

## 👨‍💻 Credits & Developer

* **Lead Developer & Maintainer**: **Abhinav Kishore (XanDron45)**
* **Repository**: [xan45/pooltoolandroid](https://github.com/xan45/pooltoolandroid)

### About This Project
This project is an experimental, highly optimized native Android port of an open-source pool game.

The application was developed using:
* **Android Studio**
* **Google AI Studio**
* **Gemini-assisted software engineering**

---

## ⚠️ Disclaimer

This project is an independent educational and experimental Android port based on open-source pool simulation concepts and is not affiliated with or endorsed by external commercial entities.

---

## 📄 License & Contributing

Distributed under the [MIT License](LICENSE.txt). Contributions, bug reports, and feature requests are welcome! Feel free to open an issue or submit a pull request.
