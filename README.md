# Bolt EV Video Player — Android Auto

A YouTube streaming app built for the **Chevy Bolt EV** head unit via Android Auto. Watch YouTube, browse your subscriptions, access purchased content, and control playback — all from your car's display.

## ⬇️ Install

**On your Android phone, tap this button:**

[![Install APK](https://img.shields.io/badge/⬇%20Download%20%26%20Install-APK-brightgreen?style=for-the-badge)](https://github.com/Aaronminer1/bolt-ev-video-player-android-auto/releases/latest/download/bolt-player.apk)

or open this page on your phone: **https://aaronminer1.github.io/bolt-ev-video-player-android-auto**

> First install only: Android will ask you to allow installs from your browser — tap **Allow**, then **Install**.

---

---

## Features

- **YouTube Search** — Search any term and get paginated results (~40 videos, with Load More)
- **Video Playback** — Actual video rendered on the head unit via `SurfaceCallback` (not just audio)
- **Playback Controls** — Play/Pause, Skip Back 15s, Skip Forward 30s, Restart, Stop
- **Google Sign-In** — WebView-based sign-in, no API key or Google Cloud Console setup needed
- **My Subscriptions** — Browse all subscribed channels; tap to search their videos
- **Movies & TV** — Free content categories + account library (Purchases, Watch Later, Watch History)
- **Stream URL** — Paste any direct video URL to play

---

## Requirements

- Android phone with **Android Auto** installed
- **Chevy Bolt EV** (or any Android Auto head unit)
- Android 8.0+ (API 26+)
- Internet connection

---

## Installation

**Option 1 — Direct install (easiest):**
1. On your Android phone, open Chrome and go to:  
   `https://aaronminer1.github.io/bolt-ev-video-player-android-auto`
2. Tap **Install**
3. If prompted, tap **Settings → Allow from this source → back → Install**
4. Open Android Auto and grant the overlay permission

**Option 2 — Direct APK link:**  
https://github.com/Aaronminer1/bolt-ev-video-player-android-auto/releases/latest/download/bolt-player.apk

---

## Building from Source

```bash
git clone https://github.com/Aaronminer1/bolt-ev-video-player-android-auto.git
cd bolt-ev-video-player-android-auto
./gradlew assembleDebug
# APK will be at app/build/outputs/apk/debug/app-debug.apk
```

**Requirements:** Android Studio / JDK 17+, Android SDK

---

## Tech Stack

| Component | Library |
|-----------|---------|
| Car UI | AndroidX Car App Library 1.4.0 |
| Video Playback | Media3 ExoPlayer 1.2.1 (HLS + DASH) |
| Stream Extraction | NewPipe Extractor v0.26.0 |
| HTTP | OkHttp |
| Auth | WebView cookie capture (no OAuth) |

---

## Architecture

```
BoltPlayerCarAppService
└── StreamingScreen          ← Main menu
    ├── YouTubeSearchScreen  ← Search input
    │   └── YouTubeResultsScreen  ← Paginated results
    ├── MoviesBrowseScreen   ← Movies & TV hub
    │   └── YouTubeLibraryScreen  ← Account library lists
    ├── SubscriptionsScreen  ← Channel list via OPML
    └── VideoPlayerScreen    ← NavigationTemplate + SurfaceCallback
            └── PlaybackController  ← ExoPlayer singleton
```

---

## Privacy & Security

- No data is sent to any third-party server
- Google sign-in cookies are stored locally in Android SharedPreferences
- The app only communicates with YouTube/Google servers directly
- Source code is fully open for inspection

---

## Known Limitations

- DRM-protected content (most purchased movies) cannot be streamed via NewPipe — the purchase list will show but playback may fail for DRM titles
- YouTube may change their page structure, which could break library scraping
- Not available on the Google Play Store (sideload only)

---

## License

Personal use. NewPipe Extractor is licensed under GPL-3.0.
