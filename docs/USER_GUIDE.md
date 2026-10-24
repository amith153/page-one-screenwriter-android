# Page One

**Write screenplays. Plan the story. Keep writing.**

Page One is a screenwriting app for Windows, macOS, Linux and Android. It writes industry-format screenplays, helps you plan the story, handles the production side (revisions, locked pages, reports, sides) and keeps you writing with goals, sprints and deadlines. The desktop app and the phone app open the same files and can edit the same script live.

This guide covers how to use every part of it. Developer notes are at the end.

---

## Contents

1. [Getting started](#1-getting-started)
2. [The home screen](#2-the-home-screen)
3. [Writing a screenplay](#3-writing-a-screenplay)
4. [Views: Script, Cards, Page, Story, Board, Production](#4-views)
5. [Notes, pictures, voice notes and links](#5-notes-pictures-voice-notes-and-links)
6. [Script Doctor](#6-script-doctor)
7. [Goals, sprints, deadlines and focus](#7-goals-sprints-deadlines-and-focus)
8. [Production: revisions, versions, lock, reports, sides](#8-production)
9. [Exporting and printing](#9-exporting-and-printing)
10. [Co-writing live, and working with your phone](#10-co-writing-live-and-working-with-your-phone)
11. [Google Drive](#11-google-drive)
12. [Writing in Kannada, Hindi and other languages](#12-writing-in-kannada-hindi-and-other-languages)
13. [Settings](#13-settings)
14. [Keyboard shortcuts](#14-keyboard-shortcuts)
15. [The phone app](#15-the-phone-app)
16. [Troubleshooting](#16-troubleshooting)
17. [For developers](#17-for-developers)

---

## 1. Getting started

**First launch.** A short welcome tour asks your name (it goes on title pages and notes) and a daily word goal. You can open the sample screenplay, *The Last Projectionist*, to look around before you start your own.

**Start a script.** On the home screen, click **New script** (or press **Ctrl+N**). To bring in an existing script, click **Import** and choose a Fountain (`.fountain`), Final Draft (`.fdx`) or Page One (`.pageone`) file.

**Where scripts are kept.** Every script is saved automatically, as you type, in `Documents/Page One/` on your computer. Nothing is uploaded unless you use Google Drive or co-writing. Settings › Files › Library folder opens it.

---

## 2. The home screen

The left rail jumps between sections and holds the main actions: **New script**, **Import**, **Join a script** (with a co-writer's code), **Drive** and **Settings**. Use the search box in the title bar to filter your scripts by title, writer or logline.

- **Continue writing.** The script you were last in, one click away.
- **Today's line.** A famous line from a film, with a still from that film behind it. Phone and desktop show the same line each day.
- **Writing goal.** Today's words against your goal, your streak and the last seven days.
- **Deadlines.** Any script with a deadline shows how many days are left and whether you're ahead or behind.
- **Your scripts.** Each script is a card: a white title page on a wash of the script's colour, with the page and scene count, the logline (the first scene's synopsis) and when you last edited it. Sort by **Recent**, **A–Z** or **Longest**. Right-click a card (or use its **⋯** button) to open, duplicate, export or delete it.
- **Discover.** Something to read and something to start from:
  - **Script to study today.** A different produced screenplay each day, with one thing to look for as you read. **Read the screenplay** opens it for free on IMSDb.
  - **Craft note.** A short, practical lesson on the craft, one a day.
  - **Story spark.** A random premise (who, what they want, what stands in the way). Click **Shuffle** for another. **Start a script from this** creates a new script with the spark in the first scene's synopsis.
  - **Free screenplays to read.** BBC Writersroom, Script Slug, IMSDb, SimplyScripts, Daily Script and 8FLiX.
  - **Fellowships, labs and competitions.** The Nicholl Fellowships, Austin Film Festival, Sundance Institute, BBC Writersroom, The Black List and Script Pipeline. Deadlines change every year, so check each site.
  - **Keep learning.** Go Into The Story, No Film School and John August.
- **Statistics.** Words this week, your streak, writing days in the last year, the size of your library, and a heatmap of your writing year.

---

## 3. Writing a screenplay

Page One formats as you type. Each paragraph is one **element**:

| Element | Shortcut | What it is |
|---|---|---|
| Scene heading | Ctrl+1 | `INT. KITCHEN - NIGHT` |
| Action | Ctrl+2 | What we see and hear |
| Character | Ctrl+3 | The name above a speech |
| Parenthetical | Ctrl+4 | `(quietly)` |
| Dialogue | Ctrl+5 | The speech |
| Transition | Ctrl+6 | `CUT TO:` |
| Shot | Ctrl+7 | `CLOSE ON THE LETTER` |
| Lyrics | Ctrl+8 | Sung lines, in italics |
| Centered | Ctrl+9 | `THE END` |
| Note | Ctrl+0 | A note to yourself that never prints |

**Moving between elements.**

- **Enter** goes to the next sensible element. A scene heading is followed by action, a character by dialogue, and dialogue by another character.
- **Tab** cycles the current element's type, and **Shift+Tab** goes back.
- **Shift+Enter** adds a line break inside an element.
- Typing `int. ` or `ext. ` turns the line into a scene heading. Typing `CUT TO:` makes a transition.

**Autocomplete.** Character names (including who is likely to speak next), locations, times of day and extensions such as (V.O.) and (O.S.) are suggested as you type. Press **Tab** or **→** to take a suggestion.

**Handled for you.**

- (CONT'D) on a character who speaks again after action.
- (MORE) and (CONT'D) when a speech breaks across pages.
- Live page breaks and page numbers.

**More editing.**

- **Dual dialogue.** Press **Ctrl+D** on a character to place their speech beside the previous one.
- **Find and replace.** Press **Ctrl+F**.
- **Undo and redo.** **Ctrl+Z** and **Ctrl+Y**.
- **Zoom.** **Ctrl+=** and **Ctrl+-**.

**Title page.** Command palette (**Ctrl+K**) › **Title page…** sets the title, credits, "based on", contact details, draft and date, copyright and registration.

**The side panels.**

- **Navigator** (**Ctrl+B**, left). Scenes, cast and places, with search and page numbers. Click a scene to jump to it.
- **Inspector** (**Ctrl+I**, right). Tabs for the current scene's sheet, Script Doctor, Notes, Changes and Stats.
- **Scene sheet.** Synopsis, colour tag, storylines, beat, a production breakdown (cast, extras, props, wardrobe, make-up, vehicles, effects, sound, set dressing, animals) and mood images.

**Command palette.** Press **Ctrl+K** to search every action in the app by name.

---

## 4. Views

Switch views with the tabs at the top of the window or **Alt+1…6**.

- **Script** (Alt+1). The editor.
- **Cards** (Alt+2). Every scene as an index card. Drag cards to reorder whole scenes, colour-tag them and filter by storyline.
- **Page** (Alt+3, or Ctrl+Shift+P). The script exactly as it prints, with (MORE), (CONT'D), scene numbers, revision colours and locked page numbers. Click any line to edit it. Only the title is bold, unless you turn on bold scene headings in Settings.
- **Story** (Alt+4):
  - **Structure and beats.** Templates for Save the Cat!, Three-act, Hero's Journey, Story Circle, short film, and one-hour and half-hour TV. Place scenes on beats, and Page One tells you where each beat lands against its target page.
  - **Story bible.** Character profiles (role, age, want, need, arc, voice, backstory) and location profiles.
  - **Script X-ray.** A pacing strip, a rhythm chart, a character-by-scene heatmap, and A/B/C storylines.
- **Board** (Alt+5). A mood board for the whole script. Drop pictures onto it, drag to rearrange, and click one to see it full size.
- **Production** (Alt+6). Revisions, the production lock and reports (see [section 8](#8-production)).

---

## 5. Notes, pictures, voice notes and links

Select words in the script and press **Ctrl+M** (or use the note button) to **pin a note** to them. The words are highlighted in your note colour. Notes appear in the Inspector's **Notes** tab, where you can:

- reply, resolve and reopen notes
- show resolved notes, or only the notes on the current line
- jump to the text a note is pinned to

If you edit the text, the note finds its words again.

**Attachments.** The buttons under a note add:

- a **picture**. Click it to open it full-window, and click anywhere or press Esc to close.
- a **voice note**, recorded straight from your microphone
- an **audio file**
- a **web link**, pasted from the clipboard, with an optional title

Attachments are stored in `Documents/Page One/media/`. A picture added on your phone shows "On the phone" on the desktop until it is synced.

---

## 6. Script Doctor

**Ctrl+Shift+K**, or the **Doctor** tab in the Inspector, checks format and consistency against 21 rules in three groups (Problems, Consistency and Polish). For example:

- a character with nothing to say
- orphaned dialogue
- "MAYA" spelled two ways
- a heading with no INT./EXT. or time of day
- a heading stranded at the bottom of a page
- dense action
- a title page with no contact details

Most issues have a one-click fix. **Tidy up** fixes every safe one at once. You can ignore a rule for one script.

---

## 7. Goals, sprints, deadlines and focus

- **Daily goal.** Words typed count towards today's goal and your streak. Change the goal in Settings.
- **Writing sprint.** Command palette › **Start a writing sprint**. Pick 10, 15, 25, 45, 60 or 90 minutes and an optional word goal. A timer runs in the title bar, a bell rings at the end, and you get a summary. Past sprints are kept.
- **Deadline.** Command palette › **Deadline…**. Set a finish date and target page count. Page One shows the pages per day you need and whether you're ahead or behind, on the home screen and in Stats.
- **Focus mode.** **F11** or **Ctrl+Shift+F** hides everything but the page. A small display shows your words and the time. Settings also has **focus line** (dims all but the current line) and **typewriter scrolling** (keeps the current line in the middle).

---

## 8. Production

- **Revisions.** In the Production view, start a revision set. Colours run Blue, Pink, Yellow, Green, Goldenrod and so on, each with a date. Changed lines get an asterisk in the margin and the page takes the set's colour. Export **revised pages only** to issue coloured pages.
- **Versions.** **Ctrl+S** saves a named snapshot. Command palette › **Versions…** lists them so you can restore one. **Track changes** compares the script with any saved version, line by line.
- **Production lock.** Freezes scene numbers and page breaks. New scenes become 12A and 12B, overflowing pages become 34A, and deleted scenes stay as OMITTED.
- **Reports.** Command palette › **Reports (CSV)…** exports scenes (with length in eighths of a page), characters, locations and breakdown sheets for a spreadsheet.
- **Sides.** Command palette › **Sides for actors…**. Choose characters to get a PDF of only their scenes, with the script's scene numbers.
- **Statistics.** The Inspector's **Stats** tab shows pages, scenes and words, dialogue against action, INT/EXT and day/night balance, how much each character speaks, and the longest scenes.

---

## 9. Exporting and printing

Click **Export** in the title bar (or press **Ctrl+E**) and pick a format:

| Format | For |
|---|---|
| **PDF** | Reading, printing and submissions: industry format in Courier Prime, US Letter or A4 |
| **Final Draft (.fdx)** | Opening in Final Draft |
| **Fountain** | Plain-text screenplay format, readable anywhere |
| **Word (.docx)**, **RTF** | Word processors |
| **Plain text**, **HTML** | Sharing and the web |
| **Page One (.pageone)** | A complete copy, including notes, story bible and production data |

The PDF options are:

- title page
- scene numbers
- bold scene headings (off by default, so only the title is bold)
- a watermark, such as a reader's name or CONFIDENTIAL, with adjustable strength
- revised pages only

**Save to Drive** sends the export straight to Google Drive instead of to a file.

**Print** (**Ctrl+P**) prints the page view directly. Use it, or Save as PDF, for scripts in Chinese, Japanese or Korean, whose fonts are too large to bundle.

---

## 10. Co-writing live, and working with your phone

Desktop and phone share one live-sync service, so a co-writer on a laptop and you on your phone can edit the same script together.

1. Open the script and click **Share** in the title bar. You get an 8-letter join code (like `K7QP-X2MA`) and a link.
2. Your co-writer clicks **Join a script** on the home screen (or "Join a shared script" on the phone) and types the code, or pastes the whole message.
3. You both edit live. Co-writers' cursors show in their colours, **follow** keeps you on their line, and **Changes** lists every edit so you can revert one.

The story bible, beats, storylines, breakdown, lock, deadline and notes are shared. Pictures and voice notes stay on the device that added them.

**Moving a script without going live.** Export a `.pageone` file and import it on the other device. You can also copy a script's file from the phone into `Documents/Page One/scripts/`, or use Google Drive.

> Co-writing needs the live-sync keys, the same three values as `collab.properties` in the phone project. Put them in `.env.local`, or in Settings › Co-writing.

---

## 11. Google Drive

Drive keeps scripts and exports in a **Page One** folder in your Google Drive. Page One can only see files it created there, never the rest of your Drive.

1. Click **Drive** on the home screen, then **Connect Google Drive**.
2. **Desktop app:** your web browser opens for Google sign-in, because Google doesn't allow sign-in inside apps. Choose your account, allow access, and the browser shows "Page One is connected". Go back to the app.
3. Use **Save this script**, **Back up all scripts**, or **Export to Drive**, and **Open** any script saved there.

The sign-in lasts an hour. After that, Page One asks you to connect again.

**One-time Google Cloud setup** (the Drive window shows these steps too):

1. In [Google Cloud Console](https://console.cloud.google.com/), go to APIs & Services › Library and enable the **Google Drive API**.
2. Go to Credentials › Create credentials › OAuth client ID and choose **Web application**. Under **Authorised redirect URIs**, add `http://localhost:47823`. To run Page One in a browser during development, also add `http://localhost:5173` under Authorised JavaScript origins.
3. On the OAuth consent screen, while the app is in **Testing**, add your Google account under **Test users**.
4. Put the client ID in `.env.local` as `VITE_GOOGLE_CLIENT_ID=…`, or paste it in Settings › Google Drive. Restart Page One after editing `.env.local`.

---

## 12. Writing in Kannada, Hindi and other languages

Type in any language. Page One knows these scripts and bundles a font for each, so they show on screen and embed in the PDF:

- Devanagari, Bengali, Gurmukhi, Gujarati, Odia, Tamil, Telugu, Kannada, Malayalam and Sinhala
- Thai
- Arabic and Hebrew, which are laid out right to left

Lines are wrapped at the real right margin, measured with the font they print in. Scripts whose marks stack above and below the letters get a taller line (about 1.3 lines) in the editor, page view and PDF, so lines never overlap. The phone app uses the same rules, so both break pages in the same places. You can mix English and another script on one line, for example a Kannada line with (V.O.).

---

## 13. Settings

**Ctrl+,** or **Settings** on the home screen.

- **Appearance.** Theme (System, Dark, Light, Pure black), the phone's twelve accent colours, paper look (white, match theme, sepia) and zoom.
- **Writing.**
  - your name
  - daily word goal
  - focus line and typewriter scrolling
  - space before scene headings
  - automatic (CONT'D)
  - (MORE)/(CONT'D) across pages
- **Pages and export.** Paper size (US Letter or A4), scene numbers, bold scene headings, and the default watermark and its strength.
- **Home.** Show or hide the film still behind today's line. A TMDb key gives real film frames; without one, stills come from Wikimedia Commons.
- **Co-writing.** Use a custom live-sync server. Leave it blank for the built-in one.
- **Google Drive.** Your OAuth client ID.
- **Files.** Open the library folder.

---

## 14. Keyboard shortcuts

Press **Ctrl+/** in the app for this list. On a Mac, use Cmd for Ctrl.

| Keys | Action |
|---|---|
| Enter / Tab / Shift+Tab | Next sensible element / cycle element type / previous type |
| Shift+Enter | Line break inside an element |
| Ctrl+1 … Ctrl+0 | Scene · Action · Character · Paren · Dialogue · Transition · Shot · Lyrics · Centered · Note |
| Ctrl+Enter | New scene |
| Alt+↑ / Alt+↓ | Previous / next scene |
| Ctrl+D | Dual dialogue |
| Ctrl+K | Command palette |
| Ctrl+F | Find and replace |
| Ctrl+M | Pin a note |
| Ctrl+Shift+K | Script Doctor |
| Ctrl+S | Save a version |
| Ctrl+E | Export |
| Ctrl+P | Print |
| Ctrl+B / Ctrl+I | Navigator / Inspector |
| Alt+1 … Alt+6 | Script · Cards · Page · Story · Board · Production |
| Ctrl+Shift+P | Page view |
| Ctrl+= / Ctrl+- | Zoom |
| F11 or Ctrl+Shift+F | Focus mode |
| Tab or → | Take the autocomplete |
| Ctrl+Z / Ctrl+Y | Undo / redo |
| Ctrl+N | New script (home screen) |
| Ctrl+, | Settings |

---

## 15. The phone app

The Android app (Android 8.0 and later) has everything above. These parts are phone-only:

- **Idea inbox.** Save ideas from any app's share menu ("Save idea"), from the home-screen widget (type or record) or from the inbox itself. Spoken ideas keep their recording. Send an idea into a script as a card, action, dialogue or note.
- **Widgets.** Idea inbox, and Writing goal (today's ring, your streak and a quote).
- **Reminders**, each with its own switch: a daily writing reminder, deadline countdowns, streak at risk, the sprint bell, a weekly inbox nudge and goal celebrations.
- **Annotated PDF.** "Include notes & references" colour-marks noted text in the export.
- **Merge.** Send a `.pageone` file to a co-writer and merge their copy back in. Conflicts are marked with notes.
- **Backup.** Automatic Google Drive backup, or a copy kept in sync in Dropbox, OneDrive or any storage app.
- **Tablets and keyboards.** On wide screens the navigator or cards sit beside the script, and the keyboard shortcuts work.
- **Spell check** with a 160,000-word dictionary that includes film slang. Character names are never flagged.

Both apps use the same robin icon, a geometric bird whose wing is the folded corner of a page.

---

## 16. Troubleshooting

| Problem | What to do |
|---|---|
| **Export says "Failed to fetch"** | This came from running the development build while its dev server re-bundled. It's fixed, so restart the app. For everyday use, install the release build (`npm run tauri build`), which doesn't need the dev server. |
| **Google Drive won't connect** | Check the four setup steps in [section 11](#11-google-drive). The usual causes are a missing `http://localhost:47823` redirect URI, your account not added as a test user, or the Drive API not enabled. If port 47823 is busy, close whatever is using it. |
| **"On the phone" instead of a picture** | The picture was added on the phone. Attachments stay on the device that added them. |
| **Co-writing or join codes don't work** | Add the three live-sync keys (see [section 10](#10-co-writing-live-and-working-with-your-phone)) and restart. |
| **Chinese, Japanese or Korean text in the PDF** | Use **Print › Save as PDF**. |
| **Scene headings are bold and you don't want them** | Settings › Bold scene headings (off by default). |

---

## 17. For developers

The desktop edition is a Tauri 2 app (Rust shell, React UI), and its core is a line-for-line port of the Android app's core.

```bash
npm install
npm run tauri dev      # the desktop app, with live reload
npm run dev            # the same UI in a browser (scripts stored in the browser)
npm test               # core tests (ported from the Android unit tests)
npm run tauri build    # installers in src-tauri/target/release/bundle
```

It needs Node 20+ and Rust (stable). On Windows, WebView2 is part of Windows 11.

**Keys.** Copy `.env.example` to `.env.local` and fill in what you need, or enter the same values in Settings. Live sync takes the three values from the phone's `collab.properties`. `VITE_TMDB_KEY` is for film stills and `VITE_GOOGLE_CLIENT_ID` is for Drive (see [section 11](#11-google-drive)).

| Layer | What | Where |
|---|---|---|
| Shell | Tauri 2 (Rust): window, native dialogs, atomic file writes, Google sign-in through the system browser (loopback on port 47823) | `src-tauri/` |
| Core | Pagination, Script Doctor, production lock, structures, Fountain, FDX, merge, `.pageone` codec. A one-to-one port of the phone's `core/` | `src/core/` |
| Editor | ProseMirror: one block per screenplay element, Final Draft keys | `src/editor/` |
| UI | React and Zustand, with a hand-built design system (Inter, Fraunces, Courier Prime). The logo is in `src/ui/Logo.tsx` | `src/ui/`, `src/styles/` |
| Export | PDF drawn line by line from the paginator (pdf-lib with embedded Noto fonts), FDX, Fountain, Word, RTF, text, HTML and CSV, to disk or Drive | `src/io/` |
| Co-writing | Firestore live sessions, a port of the phone's `collab/` | `src/collab/`, `src/state/collab.ts` |
| Media | Note attachments and the mood board, in `<library>/media/` like the phone | `src/state/media.ts` |
| Home content | Daily film line (`films.tsv`, the same pick as the phone), the Discover shelf | `src/data/` |

**Keeping the two apps in step.** `src/core/core.test.ts` carries the Android tests across with the same inputs and expectations. If you change a rule in the phone's `core/`, change it here too and keep both test suites passing. Line measuring is the one platform-specific piece: `src/io/measure.ts` on desktop and `io/PrintMeasure.kt` on the phone plug into the same `Measurer` interface.

**Icons.** The app icons are generated from `src-tauri/icons/app-icon.svg` with `npx tauri icon src-tauri/icons/app-icon.svg -o src-tauri/icons`. The phone's are the vector drawables `ic_logo_fg`, `ic_logo_mono`, `ic_splash` and `ic_brand_mark`.
