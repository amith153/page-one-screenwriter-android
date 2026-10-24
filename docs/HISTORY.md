# History and setup notes

This is the project's older README: feature notes by version and the one-time Firebase / Google setup. Replace `your-firebase-project` and the SHA-1 placeholder with your own values.

# Page One 2.2

Open this folder in Android Studio (File → Open → `the project folder`), let Gradle sync, then Run on your phone.
Same toolchain as your other projects: AGP 9.2.1, Gradle 9.4.1, Kotlin 2.2.10, Compose BOM 2026.02.01, minSdk 26.
App id is `com.amit.pageone`, so it installs alongside the old app. Runs on Android 8.0 and newer.

## What's in it

**Library** — script "covers", search, sort (recent / title / longest), rename, duplicate, delete,
import `.fountain` / `.fdx` (also via "Open with" from other apps), daily word count + streak.

**Editor**
- Paged layout with true screenplay geometry, live page breaks and page numbers, unlimited length (lazy list + background pagination).
- Format bar on the keyboard: one-tap element types, undo/redo, dual dialogue, notes, lyrics, centered, delete.
- Smart typing: Enter flow (Character → Dialogue → Character…), Enter on empty element changes type,
  Final-Draft-style backspace demotion, auto `( )` for parentheticals, `int. ` → scene heading, `CUT TO:` → transition.
- Suggestions: character names (next speaker first), `(V.O.)`/`(O.S.)`/`(CONT'D)`, INT./EXT., known locations, times of day, transitions, parentheticals.
- Automatic `(CONT'D)`, scene numbers + colour tags in the margin, coloured revision asterisks.
- Navigator drawer (all scenes, page numbers, search), Find & Replace, Focus mode, typewriter scrolling.
- Hardware keyboard: Tab cycles elements, Ctrl+Z / Ctrl+Shift+Z / Ctrl+Y.

**Index cards** — every scene as a card; long-press and drag to reorder (moves the whole scene), edit heading / synopsis / colour, add and delete scenes.

**Pro formatting** — dual dialogue, `(MORE)` / `(CONT'D)` across page breaks, keep-with-next for headings and cues,
sentence-aware action splits, revision mode with the standard colour sequence (Blue, Pink, Yellow, Green, Goldenrod, Buff, Salmon, Cherry).

**Import / export** — PDF (Courier Prime 12pt, title page, scene numbers left/right/both, revision header + asterisks, watermark),
Final Draft `.fdx` (incl. dual dialogue + synopses), Fountain. Save to any folder or share.

**Safety** — autosave ~1 s after you stop typing, atomic writes, save on app background, automatic + named versions with restore.

**Settings** — theme (System/Light/Dark/Pure black), Material You, accent colour, paper look, text size, page breaks,
scene numbers, bold headings, CONT'D, revision marks, typewriter, focus dim, keep screen on, haptics,
smart typing toggles, paper size (Letter/A4), heading spacing, MORE/CONT'D, PDF options, daily goal, default author, version cadence.

## Code map
- `core/` — pure Kotlin: model, editing rules, paginator, analysis, Fountain + FDX (unit-testable on the JVM)
- `data/` — JSON script store, versions, settings, word-goal tracker
- `io/` — PDF renderer, export/share/import
- `ui/` — theme, library, editor (screen, rows, format bar, navigator, cards, sheets), settings

## New in 2.1

**App icon** — monogram "1" whose flag is a folded page corner, with a blinking-cursor bar, on a plum→ember gradient. Adaptive (all launcher shapes), themed-icon (monochrome) layer, matching splash.

**Title page** (⋮ → Title page) — full-screen editor with live preview:
multiple credits (Written by / Screenplay by / Story by / Teleplay by / Created by / Adapted by — `&` for a team, `and` for separate writers),
adaptation ("Based on the novel “X” by Y"), draft label + date, contact (name, email, phone, address, agent/manager),
copyright and WGA/SWA registration, notes. Everything optional; PDF / FDX / Fountain all carry it.

**Collaborate** (⋮ → Collaborate)
- *Pass drafts back and forth (no setup):* **Send draft** shares a `.pageone` file; when your co-writer sends theirs back,
  open it (or **Merge theirs**) and Page One merges line by line against the last version you exchanged.
  Same line changed by both → yours stays, theirs is inserted as a NOTE right below it. Merges are undoable.
- *Live co-writing (Overleaf-style):* every element syncs within a second, you see who's online and a coloured marker on the
  line they're on, and edits made offline sync when you reconnect. Invite by link (`pageone://join/…`) or code (Library → Join icon).

### One-time live-sync setup (only you; co-writers get it from the invite)
1. console.firebase.google.com → Add project (free Spark plan).
2. Build → Firestore Database → Create database (production mode, e.g. asia-south1).
3. Firestore → Rules → paste the rules below → Publish.
4. Build → Authentication → Get started → Sign-in method → Anonymous → Enable.
5. Project settings → Your apps → Add app → Android, package `com.amit.pageone` (no SHA-1 needed).
6. Copy **Project ID**, **Web API key**, **App ID** into Page One → Settings → Collaboration → Save server.

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /scripts/{scriptId} {
      allow read, write: if request.auth != null;
      match /{rest=**} { allow read, write: if request.auth != null; }
    }
  }
}
```
Security model: like an Overleaf share link — anyone holding the invite (which contains the script's random id) can read and edit that script. Don't post invites publicly.

Notes: the co-writer currently needs an Android phone (send them the APK from Build → Build APK(s)).
The Firestore layout (`scripts/{id}/elements`, `presence`) is web-friendly, so a browser editor for iPhone/laptop users can be added on top later.

## New in 2.2

### Sharing the APK (why it wouldn't install before)
The file you were sending was `app/build/intermediates/apk/debug/app-debug.apk` — the copy Android Studio makes for **Run**.
That build is marked *test-only*, so other phones refuse it ("App not installed"). It also required Android 13+.

Now:
- **Double-click `build-share-apk.bat`** in the project folder (or Build → Generate App Bundles or APKs → Build APK(s) with the *release* variant).
  It produces **`PageOne-share.apk`** (~10 MB instead of ~75 MB), signed with your own key, for Android 8.0+.
- Send that file. On the other phone: tap it → allow "Install unknown apps" for WhatsApp/Files when asked →
  if Play Protect warns, tap **More details → Install anyway** (normal for apps not from the Play Store).
- First time only on *your* phone: uninstall the old Page One, because the old build was signed with Android Studio's debug key.
- **Back up `keystore.properties` and `app/pageone-release.jks`** (e.g. to your Drive). Every future update must be signed with this key,
  otherwise phones will refuse to update. They are git-ignored on purpose.

### New icon
A page that speaks: a white page with a folded corner and a speech-bubble tail — dialogue on the page — on Page One coral (#F0532F).
Flat, single-glyph, works as a themed (monochrome) icon too. The app's default accent is the same coral.

### Google Drive (each person connects their own account)
Settings → Cloud & backup → **Connect Google account**. Every script is backed up to a "Page One" folder in that person's Drive
(automatically ~20 s after they stop writing; optional PDF copy too), and can be restored or merged back on any phone.
Page One asks only for `drive.file`, so it can see just the files it created.

**One-time setup (you, in the same Google Cloud project as your Firebase project):**
1. console.cloud.google.com → pick the project → APIs & Services → Library → **Google Drive API** → Enable.
2. Google Auth Platform (OAuth consent screen) → Get started → App name "Page One", your email → Audience **External** → Create.
   Under *Data access* add the scope `.../auth/drive.file`. Under *Audience* press **Publish app** (drive.file is a non-sensitive scope,
   so no Google review is needed; while in Testing mode only listed test users can sign in).
3. Clients → Create client → **Android** → package name `com.amit.pageone` → SHA-1:
   `<SHA-1 of your signing key>`  (this is the fingerprint of your new signing key)
4. Done — nothing to paste into the app. Error "developer error 10" means step 3 doesn't match.

### Dropbox, OneDrive, Box, Nextcloud and other drives
⋮ → Export & share → **Keep a synced copy…** → pick a folder in that drive (its app must be installed and signed in).
Page One rewrites that PDF / Final Draft / Fountain file every time you save. Manage links in Settings → Cloud & backup.
Opening files *from* any drive works through Library → Import.

## New in 2.3 — zero-setup live co-writing
- The Firebase server (project `your-firebase-project`) is now **built into the app** (`res/values/collab.xml`). Nobody configures anything.
- ⋮ → Collaborate → **Start live session** → you get an 8-letter **join code** (e.g. `K7QP-X2MA`) → **Send invite**.
  Your co-writer opens Page One → **Join** (person-plus icon in the library) → types the code. The script opens on their phone and you both write.
- **Seeing what changed:** lines your co-writer changed get a coloured highlight in their colour. Tap the left margin of a highlighted line
  to see a word-by-word before → after (removed words struck out in red, new words in green) and **Put back** the old version.
  The live strip under the toolbar shows who's here and "N new" — tap it for the full **Changes** history (who, where, when, what).
  Tap a co-writer's name in the strip to **follow** their cursor.
- Firestore rules now also allow the `invites` collection (already published).

## New in 2.4

### Daily card (Library dashboard)
A cinematic card at the top of the library, in a projection ratio (2.39 : 1 by default — also 1.85 : 1 or 16 : 9),
with a soft matte grade, vignette and a different film still and/or quote every day. Tap it to hide the text.
Settings → Dashboard: **Daily card** (Still + quote / Film still / Quote / Off), **Frame**, **Stills from**:
- *Classic cinema* — a frame from a public-domain classic (Metropolis, Nosferatu, Sunrise, The General…), fetched from Wikimedia Commons once a day and kept for offline.
- *My stills folder* — pick any folder on the phone. Save frames you like from ShotDeck, FILMGRAB or your own screenshots there;
  one is shown per day. The file name becomes the caption, e.g. `Blade Runner 2049 (2017).jpg`.

### Spell check
Red squiggles under misspelt words in action, dialogue, parentheticals, lyrics, centered text and notes, using the phone's own spell checker
(Android Settings → System → Languages → Spell checker). Tap a word for suggestions, **Add** (personal dictionary) or **Ignore**.
Character names and ALL-CAPS words are skipped. Settings → Editor → Spell check / Personal dictionary.

### Live-sync server is private
- The built-in server's details are never shown in the app. Settings → Collaboration → **Advanced** asks for your screen lock first,
  shows blank fields for the built-in server, masks the API key, and blocks screenshots while open.
- Invites are only the 8-letter join code — the old long `P1-…` invites (which contained the server keys) are gone, and a join code
  can no longer switch your app to someone else's server.
- The keys moved out of the source code into **`collab.properties`** (project root, git-ignored — back it up with `keystore.properties`).
- **Publish the tighter Firestore rules** (Settings → Collaboration → Advanced → Copy rules, or below): nobody can list scripts or invites any more,
  so a script can only be opened by someone holding its join code.

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /scripts/{scriptId} {
      allow get, create, update: if request.auth != null;
      allow list, delete: if false;
      match /{rest=**} { allow read, write: if request.auth != null; }
    }
    match /invites/{code} {
      allow get: if request.auth != null;
      allow create: if request.auth != null && code.size() == 8
        && request.resource.data.keys().hasOnly(['s', 't', 'n', 'at']);
      allow list, update, delete: if false;
    }
  }
}
```
- Recommended: console.cloud.google.com → APIs & Services → Credentials → the Android API key → **Application restrictions: Android apps**,
  add `com.amit.pageone` with SHA-1 `<SHA-1 of your signing key>`. Then the key only works from your signed app.


## New in 2.5
- **Spell check is consistent now**: Page One has its own built-in dictionary (~160,000 words from film & TV dialogue, incl. slang and British spellings); the phone's checker is only a second opinion for names it knows. Suggestions come from both.
- **Daily card — all of cinema**: 124 films from every era and country (Hollywood, Bollywood, Japan, Korea, France, Italy, Brazil…) each with a famous line; one day in three a random well-loved film in a different language with its tagline. Frames come from **TMDB** (add a free key as `tmdbKey=` in `collab.properties`, then rebuild) and Wikimedia Commons. Without a key you still get the line + title on the matte card.
- Quote-only mode is a slim strip; turning the card off (or tapping **Hide** on it) folds it away and everything slides up.
- Word-count card: a 7-day bar chart in the middle, pages-equivalent, streak and this week's total.
- **Shared APK has no server settings at all** — the server panel lives in `src/debug` and is only compiled into builds you run from Android Studio.
- **A fresh random join code every time you send or copy an invite** (each code works for 7 days).
- **Dual dialogue side by side** in the editor, like the printed page.
- **Revision sets with dates**: each colour is dated when issued; the Revisions sheet lists every set with its line count and pages; PDF headers show "Blue Rev. 18/10/26" per revised page; optional **coloured pages** (tinted page + coloured margin edge + coloured asterisks).
- Index cards / beat board: the Dashboard icon in the editor toolbar (long-press a card and drag — the whole scene moves).

## New in 2.6
- **Daily card: ~3,250 films built in** (every era/country; ~470 with a famous line) — `app/src/main/assets/cinema/films.tsv`, one film per line: `title⇥year⇥director⇥line⇥speaker`. Add your own rows any time.
- **Several databases with fallback** — if one fails, the next fills in:
  frames: TMDB → Wikimedia Commons → Wikipedia → OMDb poster → next film; lines: built-in → Wikiquote (thousands of films) → TMDB tagline → writing quote; details: built-in → OMDb.
  Keys live in `collab.properties` (`omdbKey=` set; add `tmdbKey=` for real film frames).
- **Spell check** checks scene headings, transitions and ALL-CAPS words too; gibberish like "skk"/"klkklk" is always flagged. The phone's checker is now only trusted for Capitalised names (keyboards "learn" whatever you type).
- Settings scroll above the keyboard, so the field you're typing in (e.g. Collaboration → Your name) stays visible.
