# Bolt EV Video Player — Android Auto

A YouTube streaming app built for the **Chevy Bolt EV** head unit via Android Auto. Watch YouTube, browse your subscriptions, access purchased content, and control playback — all from your car's display.

---

## ⬇️ Quick Install

**Open this link on your Android phone and tap Install:**

**➡ https://aaronminer1.github.io/bolt-ev-video-player-android-auto**

If you have trouble with the button, jump to the [detailed install steps](#detailed-installation-steps) below.

---

## Setup Overview

There are **two separate things** you need to do before the app works in your car:

| Step | What | Why |
|------|------|-----|
| 1 | Install the APK on your phone | Gets the app onto your device |
| 2 | Enable Android Auto Developer Mode + Unknown Sources | Allows sideloaded apps to appear on the car display |

Both are required. If you skip step 2, the app will be on your phone but will never appear on the car screen.

---

## Detailed Installation Steps

### Part 1 — Install the APK

**1. Download the APK**

On your Android phone, go to:
```
https://aaronminer1.github.io/bolt-ev-video-player-android-auto
```
Tap the **Install** button. This downloads `bolt-player.apk` to your Downloads folder.

> **Using Chrome?** After download, a notification will appear at the bottom of the screen. Tap **Open** to begin installing.  
> **Can't find the file?** Open the Files app → Downloads → tap `bolt-player.apk`

---

**2. Allow installs from this source**

Android blocks APKs from the internet by default. When you tap the file you'll see a warning. The exact steps depend on your Android version:

**Android 12 or 13 (most phones as of 2024):**
1. A dialog appears: *"For your security, your phone is not allowed to install unknown apps from this source"*
2. Tap **Settings**
3. Toggle **Allow from this source** ON
4. Tap the **back arrow** — the install screen will reappear
5. Tap **Install**

**Android 8, 9, 10, or 11:**
1. Go to **Settings → Apps → Special app access → Install unknown apps**
2. Find **Chrome** (or whichever browser you used) and tap it
3. Toggle **Allow from this source** ON
4. Go back and tap the APK file again to install

> **If Play Protect warns you:** Tap **More details → Install anyway**. Play Protect flags all APKs not from the Play Store, including legitimate ones. The source code for this app is fully open at the GitHub link above.

> **If you see "App not installed":** You likely have an older version already installed. Go to **Settings → Apps → Bolt Player → Uninstall**, then try installing the downloaded APK again.

---

**3. Grant storage permission**

After installing, tap the **Bolt Player** icon in your app drawer (it launches a phone-side setup screen, not the car screen).

- Tap **Grant Storage Permission**
- Tap **Allow** on the system dialog

> **Why is this needed?** The app needs storage access to find video files on your phone. Without it, the local file browser will show empty.  
> **If you accidentally tapped Deny:** Go to Settings → Apps → Bolt Player → Permissions → Files and media → Allow.

---

### Part 2 — Enable Android Auto Developer Mode

> **This is the most commonly missed step.** Sideloaded Android Auto apps are hidden by default — they only appear on your car screen if you unlock developer mode in the Android Auto app itself.

**Step 1: Find and open the Android Auto app**

- Look for the **Android Auto** icon in your app drawer (blue steering wheel icon)
- If you can't find it: open **Settings → Connected devices → Android Auto**
- **Samsung phones:** may be listed as **Settings → General Management → Android Auto**, or in **Settings → Apps → Android Auto → Open**

> **If Android Auto is not installed:** Download it from the Play Store first, then come back to this step.

---

**Step 2: Tap your way into developer mode**

1. Inside the Android Auto app, tap the **three-dot menu (⋮)** in the top right corner
2. Tap **About**
3. Tap the **version number** (e.g., *"Version 16.3.xxxxxx"*) **10 times** in rapid succession
4. A toast message will appear: **"You are now a developer"** (or similar)

> **Nothing happening when you tap?** Make sure you are tapping the version number text itself, not the screen around it. You need to be inside the Android Auto app — not inside the Android system Settings.  
> **On older Android Auto versions:** The version may be on the main Settings page rather than an About sub-page. In that case, tap Settings → scroll down to the version number and tap 10 times.

---

**Step 3: Enable Unknown sources**

1. Go back to the Android Auto main screen
2. Open **Settings** (three-dot menu → Settings, or the gear icon)
3. Scroll down to **Developer settings** (this only appears after step 2)
4. Tap **Developer settings**
5. Enable the **Unknown sources** toggle

> **Can't find "Developer settings"?** You may need to repeat the tap-10-times step — the toast must have appeared. Also confirm you are in the Android Auto app settings, not the Android system Settings app.

---

**Step 4: Restart Android Auto**

1. Press your phone's recent apps button
2. Swipe away **Android Auto** to fully close it
3. Reconnect your phone to your car (USB cable) or reconnect wirelessly
4. **Bolt Player** should now appear in the Android Auto launcher

> **Still not showing up?** Try force-stopping Android Auto: Settings → Apps → Android Auto → Force Stop, then reconnect.  
> **On the car screen, check the app drawer:** Some head units show Android Auto apps in an "All apps" grid accessible by swiping or tapping a grid icon — the app may be there rather than the main launcher.

---

## Using the App

Once the app appears on your car screen:

| Feature | How to access |
|---------|--------------|
| **YouTube Search** | Streaming → YouTube → Search |
| **My Subscriptions** | Streaming → YouTube → Subscriptions |
| **Movies & TV** | Streaming → YouTube → Movies & TV |
| **Direct URL** | Streaming → Stream URL → type/paste a URL |
| **Local videos** | Main screen → scroll down past the menu items (shows files from phone storage) |
| **Browser** | Main screen → Browser |

**Playback controls** appear as toolbar buttons on the playback screen:
- ⏸ / ▶ Play/Pause
- ↩ 15 seconds back
- ↪ 30 seconds forward
- ⏹ Stop and return

---

## Requirements

- Android phone with **Android Auto** installed (any version that has developer settings — version 8.0+ recommended)
- **Android 10+** (API 29+)
- Android Auto-compatible car head unit (Chevy Bolt EV or any other)
- Internet connection for YouTube features

---

## Building from Source

```bash
git clone https://github.com/Aaronminer1/bolt-ev-video-player-android-auto.git
cd bolt-ev-video-player-android-auto
./gradlew assembleDebug
# APK will be at app/build/outputs/apk/debug/app-debug.apk
```

**Requirements:** JDK 17+, Android SDK (compileSdk 34)

---

## Features

- **YouTube Search** — Search any term, paginated results (~40 videos per page with Load More)
- **Video Playback** — Actual video rendered on the head unit via `SurfaceCallback` (not just audio)
- **Playback Controls** — Play/Pause, Skip Back 15s, Skip Forward 30s, Stop
- **Google Sign-In** — WebView-based sign-in, no API key or Google Cloud Console setup needed
- **My Subscriptions** — Browse all subscribed channels; tap to search their videos
- **Movies & TV** — Free content categories + account library (Purchases, Watch Later, Watch History)
- **Stream URL** — Paste any direct video/HLS/DASH URL to play
- **Local files** — Browse and play video files from phone storage
- **Browser** — Embedded web browser on the car screen

---

## Tech Stack

| Component | Library |
|-----------|---------|
| Car UI | AndroidX Car App Library 1.4.0 |
| Video Playback | Media3 ExoPlayer 1.2.1 (HLS + DASH) |
| Stream Extraction | NewPipe Extractor v0.26.0 |
| HTTP | OkHttp 4.12 |
| Auth | WebView cookie capture (no OAuth) |

---

## Architecture

```
BoltPlayerCarAppService
└── FileBrowserScreen        ← Home: local files + menu
    └── StreamingScreen      ← Streaming hub
        ├── YouTubeSearchScreen   ← Search input
        │   └── YouTubeResultsScreen  ← Paginated results
        ├── MoviesBrowseScreen   ← Movies & TV hub
        │   └── YouTubeLibraryScreen  ← Account library lists
        ├── SubscriptionsScreen  ← Channel list
        ├── UrlInputScreen       ← Direct URL entry
        ├── WebBrowserScreen     ← Embedded browser
        └── VideoPlayerScreen    ← NavigationTemplate + SurfaceCallback
                └── PlaybackController  ← ExoPlayer singleton
```

---

## Privacy & Security

- No data is sent to any third-party server
- Google sign-in cookies are stored locally in Android SharedPreferences only
- The app communicates only with YouTube/Google servers directly
- Source code is fully open for inspection

---

## Known Limitations

- DRM-protected content (most purchased movies) cannot be streamed — the purchase list will show but DRM titles will fail to play
- YouTube may change their page structure, which could break library/subscription scraping
- Not available on the Google Play Store (sideload only)
- The `Unknown sources` toggle in Android Auto developer settings must remain ON or the app will disappear from the car screen after an Android Auto update

---

## License

Personal use. NewPipe Extractor is licensed under GPL-3.0.
