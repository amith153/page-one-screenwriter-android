# Page One — developer notes (private)

For you only. This file lives in the project folder, outside the `app` module, so it is **never packed into the APK**.
The app's screens contain no developer wording: no TMDB, OMDb, Firebase, API-key or OAuth text (checked by search, 19 Oct 2026).

## 1. What must never be shared
| File | Why |
|---|---|
| `keystore.properties`, `app/pageone-release.jks` | The signing key. Lose it and nobody can update the app from your APK; leak it and anyone can sign as you. Back both up somewhere safe. |
| `collab.properties` | Firebase project id, API key and app id, the OMDb key and the (empty) TMDB key. Injected at build time. |
| `_phone_data_backup_*.tar`, `_backup_*.zip` | Your own scripts and settings. |
| `hs_err_pid*.log`, `replay_pid*.log` | Java crash dumps from Gradle. Safe to delete. |

To send the app to someone, send only `PageOne-share.apk`, which `build-share-apk.bat` produces from the `app` module.

## 2. Keys baked into the APK
`app/build.gradle.kts` copies values from `collab.properties` into string resources at build time:

| Resource | Source key | Used for |
|---|---|---|
| `collab_project_id`, `collab_api_key`, `collab_app_id` | `projectId`, `apiKey`, `appId` | Live co-writing (Firebase). Firebase web keys are designed to ship in apps; real protection is the Firestore rules (section 4). |
| `omdb_key` | `omdbKey` | Film details and poster fallback for the daily card. |
| `tmdb_key` | `tmdbKey` (currently **empty**) | Optional film frames and taglines. |

Anyone can pull those strings out of an APK. The OMDb key has a free daily quota, so if someone abuses it the card falls back to the other sources. Regenerate the key if that happens.

## 3. The "Today's frame" card (`data/Inspiration.kt`)
- Built-in catalogue of about 3,250 films, about 470 with a famous line. The pick depends on the date only, so it is the same all day and works offline from the cache.
- Frames, in order: TMDB backdrops (only with a key) → Wikimedia Commons → Wikipedia → OMDb poster → next film.
- Lines: built-in line → Wikiquote → TMDB tagline → a writing quote.
- Details: built-in → OMDb → TMDB.
- With a TMDB key, one day in three is a well-loved film in another language. Without one, the card still works from the other sources.
- The card no longer shows where an image came from (removed in 3.0).
- To turn TMDB on: themoviedb.org → Settings → API → free key → `tmdbKey=` in `collab.properties` → rebuild.

## 4. Live co-writing (Firebase)
- Firestore layout: `scripts/{id}/elements`, `presence`, `comments`, plus an `ex` blob for the story bible, beats, storylines, breakdown, lock and deadline. Local images are not shared.
- Anonymous sign-in must be enabled. Rules: signed-in users can read and write `scripts/{id}/**` (see `README.md`).
- Security model: whoever holds an invite link or join code can edit that script. Treat invites like Overleaf share links.
- Free Spark plan limits: 50k reads and 20k writes a day. Many co-writers typing at once can use them up.

## 5. Google Drive (`data/Cloud.kt`)
What it is used for. It is **not** needed to save an exported PDF to Drive; "Save to…" on the export sheet already does that through Android's file picker.
1. **Backup.** Automatic backup of `.pageone` files, and optionally an annotated PDF, to a "Page One" folder in the user's own Drive.
2. **Note references.** A PDF can only open a picture, audio or video if it sits at a URL. So each open note gets its own folder, Drive › Page One › References › <script> › “<noted words>” — <author>, holding its files plus a "Note from <author>" Google Doc (note text, replies, web links). Folder, files and doc are "anyone with the link can view". Ids are cached per note (`nref_`, `nfiles_`, `ndoc_` in the `pageone_drive` prefs); files removed from a note are moved to the Drive bin, and a folder deleted on Drive is recreated on the next export.

Scope is `drive.file`: Page One sees only files it created.

One-time Google Cloud setup (this is what fixed `UNREGISTERED_ON_API_CONSOLE`; done on 19 Oct 2026):
- APIs & Services → enable **Google Drive API**.
- OAuth consent screen → External. While it is in "Testing", only listed **test users** can sign in (limit 100). To let anyone sign in, publish the app; `drive.file` is a non-sensitive scope, so no heavy review is needed.
- Credentials → OAuth client ID → Android → package `com.amit.pageone`, SHA-1 `<SHA-1 of your signing key>`.
- Debug and release builds use the same key (`pageone-release.jks`), so one client covers both. A different keystore needs its own client.

## 6. Exported PDF
- Courier Prime 12 pt for English and `Typeface.DEFAULT` (the phone's own font) for Indic scripts such as Kannada and Hindi. A line that mixes the two draws each word in its own font on one baseline.
- Indic text prints at 11 pt in the phone's font, one 12 pt line each (no extra spacing), like Final Draft.
- Lines wrap by printed width (`TextWrap.cells`): vowel signs and viramas take no column, so Kannada lines are no longer cut short. Single-column width is still respected by shrinking the type if needed.
- Annotated PDF ("Include notes & references", on by default when a script has open notes):
  - Noted text is highlighted in the note's colour, with a dot in the margin. No appendix page and, for now, no links (19 Oct 2026). `GoogleDrive.shareNote` (one Drive folder per note) is kept for when inline links come back.
  - Links are added with PdfBox-Android in a second pass, because Android's `PdfDocument` can't create them.
- Synced copies ("Keep a synced copy") and the Drive backup PDF are now annotated the same way, using Drive links where Drive is connected.
- Not every PDF viewer supports links. Google Drive's preview and Chrome's viewer open web links but may ignore in-document jumps; the Google PDF viewer, Adobe Acrobat and most desktop readers support both.

## 7. Permissions in the manifest
`POST_NOTIFICATIONS` (reminders), `RECORD_AUDIO` (voice memos), `RECEIVE_BOOT_COMPLETED` and `SCHEDULE_EXACT_ALARM` (reminders survive a reboot and fire on time), `INTERNET`. There is no storage permission: attachments use the file picker and stay where the user keeps them.

## 8. Build and release
- Debug install on the phone: `adb install -r` keeps the data. **Back up first**: `adb backup` isn't reliable, so use the `_phone_data_backup_*.tar` approach from earlier.
- Share build: double-click `build-share-apk.bat` → `PageOne-share.apk`. R8 shrinks it to about 10 MB. `proguard-rules.pro` keeps PdfBox/FontBox and silences `com.gemalto.jp2`.
- Raise `versionCode` in `app/build.gradle.kts` for every APK you hand out (currently 8, version 3.0), or Android refuses to upgrade over it.
- Git isn't installed. Zip snapshots are the only history: `_backup_before_*.zip`.
- Unit tests: `gradlew testDebugUnitTest` (32 tests: Script Doctor, production, Indic layout).

## 9. Still to verify on the phone
- Kannada and Hindi lines with English words inside, in the exported PDF (just changed).
- Notifications (daily, deadline, streak, sprint bell), widgets, sprints, voice memos.
- First-run welcome on a fresh install (a clean emulator or after clearing data).
