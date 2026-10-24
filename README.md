# Page One for Android

**Write screenplays. Plan the story. Keep writing.**

Page One is a screenwriting app for your phone: industry-format screenplays, index cards, story structure, Script Doctor, production tools (revisions, locked pages, reports) and writing goals, sprints and streaks. It opens the same files as the [Page One desktop app](https://github.com/amith153/page-one-screenwriter) and can keep your projects and writing stats in sync with it.

## Download

Get the latest APK from the [Releases page](https://github.com/amith153/page-one-screenwriter-android/releases/latest) (`PageOne-…-android.apk`, Android 8.0 or newer).

1. Open the file on your phone.
2. When asked, allow "install unknown apps" for your browser or file manager.
3. If Play Protect warns you, choose **More details, then Install anyway**. The app is not on the Play Store.

The downloadable APK contains **no server keys**. Writing, planning, exporting to PDF, Final Draft and Fountain, goals and sprints, and **Wi-Fi sync with the desktop app and other phones** all work. Live co-writing with other people, Google Drive backup and daily film frames need a server and keys that you set up yourself (below). Your scripts stay on your devices.

## What it does

- **Write**: paged screenplay layout with true page breaks, smart Enter/Tab flow, character and location suggestions, automatic (CONT'D), dual dialogue, scene numbers and colours, find and replace, focus mode, spell check with a built-in film dictionary.
- **Plan**: index cards you can drag, story structures with worked examples, character and location bible, mood boards, notes and replies.
- **Check**: Script Doctor (format and consistency checks), X-ray reports, revision sets with colours, locked pages, versions you can restore.
- **Export**: PDF (Courier Prime, title page, watermark, revision marks), Final Draft `.fdx`, Fountain, plus Dropbox/OneDrive style linked copies.
- **Keep going**: daily word goal, streak, writing year, sprints, deadlines, reminders, and a daily film frame and line.
- **Sync** (built in, over Wi-Fi): Settings › Sync pairs this phone with the desktop app or another phone with a short code. Projects, daily words, streak, sprints and goal follow you. No account, no server, no keys.
- **Co-writing** (needs your own server): live writing with other people using join codes.

## Build it yourself

You need Android Studio (or JDK 17 and the Android SDK).

```bash
git clone https://github.com/amith153/page-one-screenwriter-android.git
cd page-one-screenwriter-android
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
```

Optional features:

1. **Live co-writing with other people**: create a free Firebase project (Firestore plus anonymous sign-in), copy `collab.properties.example` to `collab.properties` and fill in your project id, API key and app id. The Firestore rules and the full setup are in [docs/HISTORY.md](docs/HISTORY.md).
2. **Film frames**: add free `tmdbKey` / `omdbKey` values to the same file.
3. **Release signing**: copy `keystore.properties.example` to `keystore.properties` and point it at your own keystore, then `./gradlew assembleRelease`.

`collab.properties`, `keystore.properties` and every `*.jks` are git-ignored. Never commit them. See [SECURITY.md](SECURITY.md).

## Project layout

- `app/src/main/java/com/amit/pageone/core` is pure Kotlin (model, pagination, analysis, Fountain/FDX), unit-tested on the JVM.
- `…/data`, `…/collab`, `…/io` hold storage, live sync and exports; `…/ui` is Jetpack Compose.
- The desktop app is a line-for-line port of `core`, so both lay out pages the same way.

## License

MIT, © 2026 Amith ([@amith153](https://github.com/amith153)). See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
