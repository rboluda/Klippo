<p align="center">
  <img src="docs/icono/klipo_icon.png" width="128" alt="Klippo icon">
</p>

<h1 align="center">Klippo</h1>

<p align="center"><a href="README.md">Español</a> · <b>English</b></p>

<p align="center">
  <b>Download, trim and share videos from YouTube, TikTok, X, Instagram and Facebook.</b><br>
  No ads. No accounts. Nothing left behind in your gallery.<br>
  Videos delete themselves: Klippo clears its cache every hour.
</p>

<p align="center">
  <a href="https://github.com/rboluda/Klippo/releases/latest"><b>⬇️ Download the latest version (APK)</b></a>
</p>

<p align="center">
  <img src="docs/capturas/1-inicio.jpg" width="200" alt="Empty home screen">
  &nbsp;
  <img src="docs/capturas/2-calidad.jpg" width="200" alt="Choosing the quality before downloading">
  &nbsp;
  <img src="docs/capturas/3-lista.jpg" width="200" alt="Downloaded video with its shortcuts">
  &nbsp;
  <img src="docs/capturas/4-ajustes.jpg" width="104" alt="Settings">
</p>
<p align="center">
  <sub>Home · Quality for every video · Shortcuts: WhatsApp, share, gallery and edit · Settings</sub><br>
  <sub>Screenshots in Spanish; the app switches to English on phones set to English.</sub>
</p>

---

## Why Klippo

Most video downloader apps are full of ads, ask for too many permissions and fill your
gallery with files you only wanted to forward once. Klippo does the opposite:

- **Simple.** One screen with a field for the link and a list of your videos. Paste,
  pick a quality and you're done. Or, even faster, share the video from the app you're
  watching it in and Klippo starts downloading it.
- **Clean.** Videos are stored only inside Klippo, in its private cache, and **delete
  themselves**: every time you open the app, Klippo clears anything older than an hour.
  Nothing piles up on your phone, and only what you choose to save reaches your gallery.
  In a hurry? *Settings → Storage → Clear* deletes everything right away.
- **Private.** No ads, no analytics, no Firebase, no accounts, no welcome screens. Klippo
  only connects to download the video you ask for and, once a day, to check for a new
  version (you can turn that off).
- **Fast.** From the source app to WhatsApp in two taps, without touching your gallery.

## What it does

**Download**
- YouTube, TikTok, X, Instagram and Facebook (and many more sites thanks to yt-dlp).
- Instagram and X carousels: every video in the post is downloaded, each on its own.
- Paste the link (it's filled in from the clipboard automatically) or use *Share → Klippo*
  from the source app: Klippo downloads the video and opens the editor when it's done.
- When each download finishes, the notification offers *WhatsApp*, *Share* and *Save*.
- Choose the quality for every video: best available, 1080p, 720p, 480p or **audio only (M4A)**.
  H.264 is preferred so the video plays and shares without trouble on any phone.
- Progress in a notification, with a cancel button.
- For videos that require being signed in (Instagram, X, Facebook) you can sign in once from the app
  itself. Klippo never sees your password: you type it on the official website and only the
  cookies are kept.

**Edit**
- Trim the start and the end, by dragging or typing the exact time (to the tenth of a
  second), with a looping preview of the selected part.
- Crop to 9:16, 1:1, 4:5 or free: move and resize the frame over the video.
- Text over the video, with an outline or a background.
- Emojis over the video: drag them, pinch them to resize.
- Mute the exported video.
- Pixelate fixed areas (faces, number plates…): draw one or more areas and adjust them.
- Everything is applied at once on export, with FFmpeg, into an H.264 + AAC video.

**Share**
- Shortcuts on every video: send via WhatsApp, share with any app, save to the gallery
  or edit.
- *Save to gallery* creates `Movies/Klippo/klippo_<date>.mp4` (or `Music/Klippo/…m4a` for
  audio only). If you don't tap it, the video never leaves Klippo.

**Your way**
- List or grid view with thumbnails.
- Light, dark or system theme; Klippo's colors or your wallpaper's.
- Settings for the download engine, accounts, storage, notifications and updates.
- Available in Spanish and English, following your phone's language.

## Installation

Klippo is not on Google Play: you install it from its APK.

1. Download the `klippo-….apk` file from the [latest release](https://github.com/rboluda/Klippo/releases/latest).
2. Open it on your phone and allow installing apps from that source when Android asks.

Requirements: Android 13 or later on a 64-bit phone (arm64).

## Updates

Once a day Klippo checks this repository for a new version and lets you know on the home
screen. Tap **Update**, confirm in Android's installer and you're done. You can also check
manually in *Settings → Updates* or turn the check off.

The download engine (yt-dlp) is updated separately from *Settings → Download engine*:
platforms change often, and a recent engine usually fixes any download that fails.

## Building

You need Android Studio (it includes the JDK and the SDK; the Android 17 platform, API 37,
is required).

```bash
./gradlew assembleDebug
```

The APK ends up in `apk/`. Klippo uses semantic versioning (`MAJOR.MINOR.PATCH`, set in
`app/build.gradle.kts`); debug builds add the date and time so each one can be told apart.

Tech: Kotlin, Jetpack Compose and Material 3, Media3 (ExoPlayer) for the preview,
[youtubedl-android](https://github.com/yausername/youtubedl-android) to bundle yt-dlp and FFmpeg.

## Credits

Klippo is made possible by these free software projects:

| Project | License |
| --- | --- |
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | Unlicense |
| [FFmpeg](https://ffmpeg.org) | GPL |
| [youtubedl-android](https://github.com/yausername/youtubedl-android) | GPL-3.0 |
| [Jetpack Compose · Media3](https://developer.android.com/jetpack) | Apache 2.0 |

Respect copyright and each platform's terms: only download content you have the right to keep.

## License

Klippo is free software under the [GNU General Public License v3.0 or later](LICENSE)
(GPL-3.0-or-later): you can use, study, modify and redistribute it, as long as the versions
you distribute remain free and come with their source code. It's the license required by
the components it bundles, such as youtubedl-android and FFmpeg with libx264.

---

<p align="center">Made by <a href="https://github.com/rboluda">rboluda</a></p>
