# Bolt EV Video Player — User Manual

---

## Table of Contents

1. [First-Time Setup](#1-first-time-setup)
2. [Connecting to Android Auto](#2-connecting-to-android-auto)
3. [Navigating the App](#3-navigating-the-app)
4. [Searching YouTube](#4-searching-youtube)
5. [Playing a Video](#5-playing-a-video)
6. [Playback Controls](#6-playback-controls)
7. [Signing In with Google](#7-signing-in-with-google)
8. [My Subscriptions](#8-my-subscriptions)
9. [Movies & TV](#9-movies--tv)
10. [Streaming a URL](#10-streaming-a-url)
11. [Signing Out](#11-signing-out)
12. [Troubleshooting](#12-troubleshooting)

---

## 1. First-Time Setup

1. Install `app-debug.apk` on your Android phone (see README for steps)
2. Open the **Bolt Player** app on your phone once — this grants necessary permissions
3. Accept any permission prompts (internet access, etc.)
4. The app is now ready to use via Android Auto

---

## 2. Connecting to Android Auto

1. Plug your phone into the Bolt EV's USB port **or** connect wirelessly if your head unit supports it
2. Android Auto will launch automatically on the head unit
3. Find and tap the **Bolt Player** icon in the Android Auto app launcher
4. The Streaming menu will appear on the head unit display

---

## 3. Navigating the App

The main **Streaming** screen has the following options:

| Row | Description |
|-----|-------------|
| **YouTube** | Search for any YouTube video |
| **Movies & TV** | Browse free movies or your account library |
| **My Subscriptions** | Your subscribed channels *(requires sign-in)* |
| **Stream a URL** | Enter a direct video URL to play |
| **Sign In** | Sign in with your Google account |
| **Sign Out** | Sign out *(only shown when signed in)* |

Use the head unit's touch screen or rotary knob to navigate.

---

## 4. Searching YouTube

1. Tap **YouTube** from the Streaming menu
2. A search box appears — use the on-screen keyboard or voice input to type your search
3. Tap the search button (magnifying glass)
4. Results load — typically 20–40 videos
5. Scroll down to the **▼ Load More** row to fetch additional results
6. Tap any video to start playing

---

## 5. Playing a Video

1. Tap any video row from search results, subscriptions, or library
2. The app extracts the stream from YouTube (takes 2–5 seconds)
3. Video begins playing on the head unit display
4. The **Now Playing** screen shows the video title and controls

> **Note:** Some videos may fail to load if YouTube has restricted them or they require a login. The app will show an error message in that case.

---

## 6. Playback Controls

On the **Now Playing** screen:

### Side Action Strip (left/right side)
| Button | Action |
|--------|--------|
| ← Back | Return to previous screen (keeps video playing) |
| ⏮ | Skip back 15 seconds |
| ▶ / ⏸ | Play / Pause (icon changes based on state) |
| ⏭ | Skip forward 30 seconds |

### Map Action Strip (top)
| Button | Action |
|--------|--------|
| ↺ | Restart from beginning |
| ⏹ | Stop and release the player |

---

## 7. Signing In with Google

Sign-in is required for **Subscriptions**, **Watch Later**, **Watch History**, and **My Purchases**.

1. From the **Streaming** menu, tap **Sign In**
2. A browser window opens **on your phone**
3. Sign in with your Google account using the normal YouTube/Google sign-in flow
4. Once you land on the YouTube home page, the app automatically captures your session
5. A toast notification says **"Signed in as [account name]"**
6. The browser window closes automatically
7. Return to the car screen — **My Subscriptions** and account library items are now available

> **Important:** Sign-in happens on your **phone screen**, not the car display. The car display will update automatically after sign-in completes.

---

## 8. My Subscriptions

Requires sign-in.

1. From the **Streaming** menu, tap **My Subscriptions**
2. Your subscribed channels load in alphabetical order
3. Tap any channel to see a search of their videos
4. Tap a video to play it

> **Note:** The subscription list uses YouTube's OPML export which may take a few seconds to load for large subscription lists.

---

## 9. Movies & TV

Tap **Movies & TV** from the Streaming menu. You'll see:

### Account Library *(requires sign-in)*
| Row | Description |
|-----|-------------|
| **My Purchases / Rentals** | Movies and shows you've bought or rented on YouTube |
| **Watch Later** | Videos you've saved to Watch Later |
| **Watch History** | Recently watched videos |

### Free Content *(no sign-in needed)*
| Row | Description |
|-----|-------------|
| Free Movies | Full-length free movies on YouTube |
| New Releases | Recent movies (2025–2026) |
| Action Movies | Free action movies |
| Comedy Movies | Free comedy movies |
| Horror Movies | Free horror movies |
| Documentaries | Full documentaries |
| TV Show Episodes | Free TV show episodes |

> **Note on Purchases:** YouTube purchases are DRM-protected. The list will show your purchased titles, but some may not play due to DRM restrictions in the stream extractor. Free/ad-supported content plays reliably.

---

## 10. Streaming a URL

To play a specific video URL:

1. From the Streaming menu, tap **Stream a URL**
2. Enter a YouTube URL in the format: `https://www.youtube.com/watch?v=XXXXXXXXXXX`
3. Tap the play button
4. The video will start playing on the head unit

---

## 11. Signing Out

1. From the **Streaming** menu, tap **Sign Out**
2. Your session cookies are deleted locally
3. The **Sign Out** row is replaced with **Sign In**
4. Account-specific features (Subscriptions, Watch Later, etc.) are hidden until you sign in again

---

## 12. Troubleshooting

### "No videos found" on Watch History / Watch Later / Purchases
- Sign out and sign back in to refresh your session cookies
- These pages require an active authenticated session

### Video fails to play / "Could not extract stream"
- Some videos are geo-restricted or age-gated and cannot be extracted
- Try a different video
- If many videos fail, sign out and sign back in

### "Please sign in first" even though I'm signed in
- Your session may have expired — tap **Sign Out** then **Sign In** again
- Make sure you complete the full sign-in flow until you see the "Signed in as..." toast on your phone

### App doesn't appear in Android Auto
- Make sure Android Auto is up to date
- On your phone, go to Settings → Apps → Android Auto → Special App Access → enable the app
- Restart Android Auto

### Sign-in browser doesn't close automatically
- Make sure you complete the full sign-in (enter email, password, and any 2FA)
- The browser closes when it detects the YouTube home page — if you land on a different page, complete navigation to youtube.com

### Subscriptions list is empty
- The OPML export can take up to 30 seconds for large subscription lists
- If it's empty after waiting, try signing out and back in

### Video plays audio but no picture on head unit
- This is a known issue with some Android Auto sessions — disconnect and reconnect USB, then try again

---

*Bolt EV Video Player — built for Android Auto car head units*
