**Android**

A native Android prototype is in `android/`. It provides a landscape 3D pool table,
touch-to-aim-and-shoot controls, a free-play rack, and simplified ball collision and
pocketing physics. It is a separate Android implementation; it does not yet provide
the Python package's full physics, rulesets, or analysis API.

Build a debug APK from Windows with:

```powershell
cd android
.\gradlew.bat assembleDebug
```

The APK is written to
`android\app\build\outputs\apk\debug\app-debug.apk`.
