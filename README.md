# PvZ AI Agent v2 — Android-native bridge

This version removes the Termux ADB dependency and all Python image libraries.

## Architecture

PvZ2 (same phone)
→ Android Accessibility Service
→ local HTTP bridge on 127.0.0.1:8765
→ Python agent in Termux
→ OmniRoute Vision model
→ JSON action
→ Accessibility tap/swipe

The Android service captures screenshots with Android's native AccessibilityService `takeScreenshot()` API and compresses them to JPEG (quality 55) before returning them to the Python agent. No Pillow/OpenCV/NumPy are required.

## 1. Build APK

Open `android-bridge` in Android Studio, or use the included GitHub Actions workflow `.github/workflows/build-bridge.yml`.

The APK output is `app/build/outputs/apk/debug/app-debug.apk`.

## 2. Android setup

Install the APK → open **PvZ AI Bridge** → tap **MỞ CÀI ĐẶT ACCESSIBILITY** → enable **PvZ AI Bridge**.

The bridge listens only on `127.0.0.1:8765`.

## 3. Termux

```bash
cd ~/pvz-ai-agent-v2/python-agent
python -m pip install -r requirements.txt
cp .env.example .env
nano .env
```

Set your OmniRoute values. Then test:

```bash
python main.py --dry-run
```

Expected first lines:

```text
[bridge] {'ok': True, 'service': 'pvz-ai-bridge', 'port': 8765}
[screen] 1080x2400
Mode: DRY RUN
Transport: Android Accessibility bridge (no ADB/Pillow/OpenCV)
```

Only after the vision decisions look sensible should you set `DRY_RUN=0` and run `python main.py`.

## Notes

- The bridge does not save screenshots to storage.
- JPEG is generated in memory and immediately returned.
- Screenshot capture is rate-limited by the Python agent.
- The Python agent skips repeated identical images.
- This is a prototype; game UI/state detection should be made more structured before relying on autonomous gameplay.
