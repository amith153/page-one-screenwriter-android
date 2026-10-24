package com.amit.pageone.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

data class Quote(val text: String, val by: String)

/** A film for the daily card, with one of its famous lines (if known). */
data class Film(val title: String, val year: Int, val director: String, val line: String = "", val by: String = "")

/**
 * Today's frame, ready to draw. [line]/[by] is the film's dialogue or tagline, if we found one.
 * [poster] is true when the only picture we could find was a (portrait) poster, not a frame.
 */
data class Still(
    val bitmap: Bitmap?, val title: String, val credit: String,
    val line: String = "", val by: String = "", val via: String = "", val poster: Boolean = false,
)

/**
 * The dashboard's "one a day" content.
 *
 * Films come from a built-in catalogue of ~3,250 films (every era and country; ~470 with a
 * famous line), plus — when a TMDB key is built in — one day in three a random well-loved
 * film in another language. For each film, several free databases are tried in turn, so if
 * one is down or has nothing, the next one fills in:
 *
 *  - Frames:   TMDB backdrops → Wikimedia Commons → Wikipedia → OMDb poster → next film
 *  - Lines:    built-in line → Wikiquote → TMDB tagline → a writing quote
 *  - Details:  built-in → OMDb → TMDB
 *
 * Everything is picked from the date, so the card changes at midnight and stays the same all
 * day, and is cached so it works offline.
 */
object Inspiration {

    fun today(): Long = LocalDate.now().toEpochDay()

    /** A stable shuffle of 0 until n, then the slot for [day]. */
    private fun pick(n: Int, day: Long, salt: Int): Int {
        if (n <= 0) return 0
        val cycle = Math.floorDiv(day, n.toLong())
        val order = (0 until n).shuffled(java.util.Random(cycle * 31 + salt))
        return order[Math.floorMod(day, n.toLong()).toInt()]
    }

    fun quoteFor(day: Long = today()): Quote = QUOTES[pick(QUOTES.size, day, 7)]

    // ------------------------------------------------------------------ the film catalogue

    @Volatile private var catalogue: List<Film>? = null
    @Volatile private var withLines: List<Film>? = null

    /** ~3,250 films from assets/cinema/films.tsv (title, year, director, line, speaker). */
    fun films(c: Context): List<Film> {
        catalogue?.let { return it }
        synchronized(this) {
            catalogue?.let { return it }
            val list = runCatching {
                c.assets.open("cinema/films.tsv").bufferedReader().readLines().mapNotNull { ln ->
                    val p = ln.split('\t')
                    if (p.isEmpty() || p[0].isBlank()) null
                    else Film(p[0], p.getOrNull(1)?.toIntOrNull() ?: 0, p.getOrNull(2).orEmpty(), p.getOrNull(3).orEmpty(), p.getOrNull(4).orEmpty())
                }
            }.getOrDefault(emptyList()).ifEmpty { listOf(Film("Casablanca", 1942, "Michael Curtiz", "Here's looking at you, kid.", "Rick")) }
            catalogue = list
            withLines = list.filter { it.line.isNotBlank() }
            return list
        }
    }

    fun filmFor(c: Context, day: Long, attempt: Int = 0): Film {
        val all = films(c)
        // Each retry jumps somewhere far away in the list, not just to the next film.
        return all[Math.floorMod(pick(all.size, day, 13) + attempt * 977, all.size)]
    }

    private fun caption(f: Film) = listOf(f.by, if (f.year > 0) "${f.title} (${f.year})" else f.title).filter { it.isNotBlank() }.joinToString(", ")

    /** For "Quote" mode: alternate between a writing quote and a famous line from a film (works offline). */
    fun lineFor(c: Context, day: Long = today()): Quote {
        films(c)
        val lines = withLines.orEmpty()
        if (Math.floorMod(day, 2L) == 0L || lines.isEmpty()) return quoteFor(day)
        val f = lines[pick(lines.size, day, 41)]
        return Quote(f.line, caption(f))
    }

    // ------------------------------------------------------------------ stills

    private const val UA = "PageOne/2.6 (Android screenwriting app; personal use)"

    private fun dir(c: Context) = File(c.filesDir, "stills3").apply { mkdirs() }

    /** Today's still from the chosen source, or the film's line on its own when no picture can be found. */
    suspend fun stillFor(c: Context, source: StillsSource, folder: String, day: Long = today()): Still? = withContext(Dispatchers.IO) {
        when (source) {
            StillsSource.FOLDER -> if (folder.isBlank()) null else runCatching { fromFolder(c, Uri.parse(folder), day) }.getOrNull()
            StillsSource.CINEMA -> runCatching { cinema(c, day) }.getOrNull()
        }
    }

    // ---- your own folder (frames saved from ShotDeck, FILMGRAB, screenshots…)

    private fun fromFolder(c: Context, tree: Uri, day: Long): Still? {
        val kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val files = ArrayList<Pair<String, String>>() // id, name
        c.contentResolver.query(
            kids,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
            null, null, null,
        )?.use { cur ->
            while (cur.moveToNext()) {
                val mime = cur.getString(2) ?: ""
                if (mime.startsWith("image/")) files += cur.getString(0) to (cur.getString(1) ?: "")
            }
        }
        if (files.isEmpty()) return null
        files.sortBy { it.second.lowercase() }
        val (id, name) = files[pick(files.size, day, 29)]
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
        val bmp = decode({ c.contentResolver.openInputStream(uri)!! }) ?: return null
        return Still(bmp, titleFromFileName(name), "", via = "folder")
    }

    /** "blade_runner_2049 (2017).jpg" → "Blade Runner 2049 (2017)". */
    fun titleFromFileName(name: String): String {
        val base = name.substringBeforeLast('.').replace('_', ' ').replace(Regex("\\s+-\\s+|-{2,}"), " · ").trim()
        return base.split(' ').joinToString(" ") { w -> if (w.length > 1 && w.all { it.isLowerCase() }) w.replaceFirstChar { it.uppercase() } else w }
    }

    // ---- all of cinema, from several databases

    private fun key(c: Context, id: Int): String = runCatching { c.getString(id) }.getOrDefault("").trim()

    private fun cinema(c: Context, day: Long): Still? {
        val d = dir(c)
        val img = File(d, "$day.jpg")
        val meta = File(d, "$day.json")
        if (meta.exists()) load(img, meta)?.let { return it }
        val tmdb = key(c, com.amit.pageone.R.string.tmdb_key)
        val omdb = key(c, com.amit.pageone.R.string.omdb_key)

        var lineOnly: Film? = null
        // One day in three (with TMDB): a well-loved film from anywhere, in a different language each time.
        if (tmdb.isNotBlank() && Math.floorMod(day, 3L) == 2L) {
            runCatching { tmdbDiscover(tmdb, day, img) }.getOrNull()?.let { f0 ->
                val f = if (f0.line.isBlank()) f0.withLine(wikiquote(f0, day)) else f0
                save(meta, f, "TMDB", false); prune(d, day); return load(img, meta)
            }
        }
        for (attempt in 0 until 6) {
            var f = filmFor(c, day, attempt)
            if (omdb.isNotBlank() && (f.director.isBlank() || f.year == 0)) f = runCatching { omdbFill(omdb, f) }.getOrDefault(f)
            if (f.line.isBlank()) f = f.withLine(runCatching { wikiquote(f, day) }.getOrNull())
            var tagline = ""
            var poster = false
            val via = when {
                tmdb.isNotBlank() && runCatching { tmdbStill(tmdb, f, day, img) { tagline = it } }.getOrDefault(false) -> "TMDB"
                runCatching { commonsStill(f, day, img) }.getOrDefault(false) -> "Wikimedia Commons"
                runCatching { wikipediaStill(f, img) }.getOrDefault(false) -> "Wikipedia"
                omdb.isNotBlank() && runCatching { omdbPoster(omdb, f, img) }.getOrDefault(false) -> { poster = true; "OMDb" }
                else -> null
            }
            if (f.line.isBlank() && tagline.isNotBlank()) f = f.copy(line = tagline, by = "")
            if (via != null) { save(meta, f, via, poster); prune(d, day); return load(img, meta) }
            if (lineOnly == null && f.line.isNotBlank()) lineOnly = f
        }
        // Nothing reachable today (offline?): show a film and its line on the matte card.
        val f = lineOnly ?: filmFor(c, day)
        return Still(null, title(f), f.director, f.line, f.by)
    }

    private fun Film.withLine(q: Pair<String, String>?): Film = if (q == null) this else copy(line = q.first, by = q.second)

    private fun title(f: Film) = f.title.uppercase() + if (f.year > 0) " (${f.year})" else ""

    private fun save(meta: File, f: Film, via: String, poster: Boolean) {
        meta.writeText(
            JSONObject().put("t", title(f)).put("c", f.director)
                .put("l", f.line).put("b", f.by).put("v", via).put("p", poster).toString()
        )
    }

    private fun prune(d: File, day: Long) {
        d.listFiles()?.filter { it.nameWithoutExtension.toLongOrNull()?.let { n -> n < day - 3 } == true }?.forEach { it.delete() }
    }

    private fun load(img: File, meta: File): Still? {
        if (!img.exists()) return null
        val bmp = decode({ img.inputStream() }) ?: return null
        val o = runCatching { JSONObject(meta.readText()) }.getOrNull()
        return Still(
            bmp, o?.optString("t") ?: "", o?.optString("c") ?: "", o?.optString("l") ?: "", o?.optString("b") ?: "",
            o?.optString("v") ?: "", o?.optBoolean("p") ?: false,
        )
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun json(url: String, bearer: String? = null): JSONObject? =
        get(url, bearer)?.let { runCatching { JSONObject(it.toString(Charsets.UTF_8)) }.getOrNull() }

    private fun isImage(bytes: ByteArray): Boolean {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        return o.outWidth > 0
    }

    private fun saveImage(url: String, out: File): Boolean {
        val bytes = get(url) ?: return false
        if (!isImage(bytes)) return false
        out.writeBytes(bytes)
        return true
    }

    // ---- TMDB (frames + taglines + discovery)

    private fun tmdbGet(key: String, path: String): JSONObject? {
        val bearer = key.startsWith("eyJ")
        val url = "https://api.themoviedb.org/3$path" + if (bearer) "" else (if ('?' in path) "&" else "?") + "api_key=" + key
        return json(url, if (bearer) key else null)
    }

    private fun tmdbStill(key: String, film: Film, day: Long, out: File, onTagline: (String) -> Unit): Boolean {
        val y = if (film.year > 0) "&year=${film.year}" else ""
        val res = tmdbGet(key, "/search/movie?query=${enc(film.title)}$y&include_adult=false")?.optJSONArray("results") ?: return false
        if (res.length() == 0) return false
        val id = res.getJSONObject(0).optInt("id")
        if (film.line.isBlank()) runCatching { tmdbGet(key, "/movie/$id")?.optString("tagline")?.let(onTagline) }
        return tmdbBackdrop(key, id, day, out)
    }

    private fun tmdbBackdrop(key: String, id: Int, day: Long, out: File): Boolean {
        val imgs = tmdbGet(key, "/movie/$id/images?include_image_language=null,en")?.optJSONArray("backdrops") ?: return false
        val all = (0 until imgs.length()).map { imgs.getJSONObject(it) }
        val clean = all.filter { it.isNull("iso_639_1") }.ifEmpty { all }
            .sortedByDescending { it.optDouble("vote_count", 0.0) }.take(12)
        if (clean.isEmpty()) return false
        val path = clean[pick(clean.size, day, 5)].optString("file_path")
        if (path.isBlank()) return false
        return saveImage("https://image.tmdb.org/t/p/w1280$path", out)
    }

    private val LANGS = listOf("hi", "ja", "ko", "fr", "it", "es", "de", "zh", "ta", "ml", "bn", "fa", "pt", "sv", "en", "ru", "te", "da", "tr", "pl", "mr", "kn", "ar", "cn", "th")

    private fun tmdbDiscover(key: String, day: Long, out: File): Film? {
        val lang = LANGS[pick(LANGS.size, day, 17)]
        val page = 1 + pick(5, day, 19)
        val res = tmdbGet(key, "/discover/movie?sort_by=vote_count.desc&vote_count.gte=100&with_original_language=$lang&page=$page")
            ?.optJSONArray("results") ?: return null
        if (res.length() == 0) return null
        val m = res.getJSONObject(pick(res.length(), day, 23))
        val id = m.optInt("id")
        val details = tmdbGet(key, "/movie/$id?append_to_response=credits") ?: return null
        if (!tmdbBackdrop(key, id, day, out)) return null
        val crew = details.optJSONObject("credits")?.optJSONArray("crew")
        val director = crew?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.optString("job") == "Director" }?.optString("name") }.orEmpty()
        val year = details.optString("release_date").take(4).toIntOrNull() ?: 0
        val title = details.optString("title").ifBlank { details.optString("original_title") }
        return Film(title, year, director, details.optString("tagline"), "")
    }

    // ---- OMDb (details + poster)

    private fun omdbGet(key: String, f: Film): JSONObject? {
        val y = if (f.year > 0) "&y=${f.year}" else ""
        val o = json("https://www.omdbapi.com/?apikey=${enc(key)}&type=movie&t=${enc(f.title)}$y") ?: return null
        return if (o.optString("Response") == "True") o else null
    }

    private fun omdbFill(key: String, f: Film): Film {
        val o = omdbGet(key, f) ?: return f
        val dir = o.optString("Director").takeIf { it.isNotBlank() && it != "N/A" } ?: f.director
        val yr = o.optString("Year").take(4).toIntOrNull() ?: f.year
        return f.copy(director = dir, year = if (f.year == 0) yr else f.year)
    }

    private fun omdbPoster(key: String, f: Film, out: File): Boolean {
        val url = omdbGet(key, f)?.optString("Poster")?.takeIf { it.startsWith("http") } ?: return false
        // Ask Amazon's image server for a bigger copy than OMDb's 300px default.
        return saveImage(url.replace(Regex("_SX\\d+"), "_SX1200"), out) || saveImage(url, out)
    }

    // ---- Wikiquote (famous lines for thousands of films)

    private val SKIP_SECTIONS = Regex("cast|external|see also|about|review|quotes about|sources|notes|references|production|crew|soundtrack", RegexOption.IGNORE_CASE)
    private const val BOLD = "'''"
    private const val ITALIC = "''"

    private fun clean(s: String): String = s
        .replace(Regex("<ref[^>]*/>"), "").replace(Regex("(?s)<ref.*?</ref>"), "")
        .replace(Regex("\\{\\{[^}]*\\}\\}"), "")
        .replace(Regex("\\[\\[(?:File|Image):[^\\]]*\\]\\]"), "")
        .replace(Regex("\\[\\[[^\\]|]*\\|([^\\]]*)\\]\\]"), "$1")
        .replace(Regex("\\[\\[([^\\]]*)\\]\\]"), "$1")
        .replace(Regex("\\[https?://\\S+ ([^\\]]*)\\]"), "$1")
        .replace(Regex("<[^>]+>"), " ")
        .replace(BOLD, "").replace(ITALIC, "")
        .replace(Regex("\\s+"), " ").trim()

    private val HEADING = Regex("^==+\\s*(.*?)\\s*==+\\s*$")
    private val DIALOGUE_LINE = Regex("^:+\\s*$BOLD(.+?)$BOLD:?\\s*(.+)$")

    /** A famous line from the film's Wikiquote page: (line, speaker). */
    fun wikiquote(f: Film, day: Long): Pair<String, String>? {
        val names = buildList {
            if (f.year > 0) add("${f.title} (${f.year} film)")
            add("${f.title} (film)")
            add(f.title)
        }
        for (n in names) {
            val o = json("https://en.wikiquote.org/w/api.php?action=parse&format=json&formatversion=2&redirects=1&prop=wikitext&page=${enc(n)}") ?: continue
            val wt = o.optJSONObject("parse")?.optString("wikitext") ?: continue
            if (n == f.title && !wt.take(3000).contains("film", ignoreCase = true)) continue // a book or a person, not the film
            val picks = ArrayList<Pair<String, String>>()
            var section = ""
            for (raw in wt.lineSequence()) {
                val h = HEADING.find(raw)
                if (h != null) { section = clean(h.groupValues[1]); continue }
                if (section.isBlank() || SKIP_SECTIONS.containsMatchIn(section)) continue
                val taglines = section.equals("Taglines", true)
                val speakerSection = !section.equals("Dialogue", true) && !taglines
                var text: String
                var who: String
                if (raw.startsWith("* ") && !raw.startsWith("**")) {
                    text = clean(raw.removePrefix("* ")); who = if (speakerSection) section else ""
                } else if (raw.startsWith(":")) {
                    val m = DIALOGUE_LINE.find(raw) ?: continue
                    text = clean(m.groupValues[2]).removePrefix(":").trim(); who = clean(m.groupValues[1]).removeSuffix(":")
                } else continue
                if (text.length !in 12..200 || "http" in text || text.startsWith("(")) continue
                picks += text to (if (taglines) "" else who)
            }
            if (picks.isNotEmpty()) return picks[pick(picks.size, day, 31)]
        }
        return null
    }

    // ---- Wikimedia Commons + Wikipedia (frames)

    private val SKIP = Regex("poster|lobby|logo|cover|advert|program|herald|newspaper|magazine|signature|grave|book|card", RegexOption.IGNORE_CASE)

    private fun commonsStill(film: Film, day: Long, out: File): Boolean {
        val queries = buildList {
            if (film.year > 0) add("incategory:\"${film.title} (${film.year} film)\" filetype:bitmap")
            add("\"${film.title}\" ${if (film.year > 0) film.year.toString() else ""} film screenshot filetype:bitmap")
        }
        for (q in queries) {
            val urls = runCatching { search(q) }.getOrDefault(emptyList())
            if (urls.isEmpty()) continue
            if (saveImage(urls[pick(urls.size, day, 3)], out)) return true
        }
        return false
    }

    /** The lead image of the film's Wikipedia article — only if it's wide (a frame, not a poster). */
    private fun wikipediaStill(f: Film, out: File): Boolean {
        val names = buildList { if (f.year > 0) add("${f.title} (${f.year} film)"); add("${f.title} (film)"); add(f.title) }
        for (n in names) {
            val path = URLEncoder.encode(n.replace(' ', '_'), "UTF-8").replace("+", "%20")
            val o = json("https://en.wikipedia.org/api/rest_v1/page/summary/$path") ?: continue
            if (!o.optString("description").contains("film", true)) continue
            val im = o.optJSONObject("originalimage") ?: continue
            val w = im.optInt("width"); val h = im.optInt("height")
            if (h <= 0 || w.toFloat() / h < 1.3f || w < 640) return false // only a poster
            return saveImage(im.optString("source"), out)
        }
        return false
    }

    /** Landscape stills (not posters) matching a Commons search, as ~1600px thumbnail URLs. */
    private fun search(query: String): List<String> {
        val api = "https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2" +
            "&generator=search&gsrnamespace=6&gsrlimit=40&gsrsearch=" + enc(query) +
            "&prop=imageinfo&iiprop=url%7Csize%7Cmime&iiurlwidth=1600"
        val pages = json(api)?.optJSONObject("query")?.optJSONArray("pages") ?: return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until pages.length()) {
            val p = pages.getJSONObject(i)
            if (SKIP.containsMatchIn(p.optString("title"))) continue
            val info = p.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
            val w = info.optInt("width"); val h = info.optInt("height")
            val mime = info.optString("mime")
            if (h <= 0 || w < 640 || w.toFloat() / h < 1.25f) continue
            if (mime != "image/jpeg" && mime != "image/png") continue
            val u = info.optString("thumburl").ifBlank { info.optString("url") }
            if (u.isNotBlank()) out += u
        }
        return out.sorted()
    }

    private fun get(url: String, bearer: String? = null): ByteArray? {
        return try {
            val con = URL(url).openConnection() as HttpURLConnection
            try {
                con.connectTimeout = 7000; con.readTimeout = 12000
                con.instanceFollowRedirects = true
                con.setRequestProperty("User-Agent", UA)
                if (bearer != null) con.setRequestProperty("Authorization", "Bearer $bearer")
                if (con.responseCode != 200) null else con.inputStream.use { it.readBytes() }
            } finally { con.disconnect() }
        } catch (e: Exception) { null }
    }

    /** Decode no bigger than ~1600px wide, so a 6K frame from your folder doesn't eat memory. */
    private fun decode(open: () -> java.io.InputStream): Bitmap? = runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, o) }
        var sample = 1
        while (o.outWidth / (sample * 2) >= 1600) sample *= 2
        open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }.getOrNull()

    // ------------------------------------------------------------------ writing quotes

    val QUOTES = listOf(
        Quote("Cinema is a matter of what's in the frame and what's out.", "Martin Scorsese"),
        Quote("A film is — or should be — more like music than like fiction.", "Stanley Kubrick"),
        Quote("If it can be written, or thought, it can be filmed.", "Stanley Kubrick"),
        Quote("Drama is life with the dull bits cut out.", "Alfred Hitchcock"),
        Quote("To make a great film you need three things — the script, the script and the script.", "Alfred Hitchcock"),
        Quote("Nobody knows anything.", "William Goldman"),
        Quote("Screenplays are structure.", "William Goldman"),
        Quote("Make visible what, without you, might perhaps never have been seen.", "Robert Bresson"),
        Quote("The enemy of art is the absence of limitations.", "Orson Welles"),
        Quote("A story should have a beginning, a middle and an end, but not necessarily in that order.", "Jean-Luc Godard"),
        Quote("Photography is truth. The cinema is truth twenty-four times per second.", "Jean-Luc Godard"),
        Quote("There are no rules in filmmaking. Only sins. And the cardinal sin is dullness.", "Frank Capra"),
        Quote("Film is like a battleground. Love, hate, action, violence, death. In one word: emotion.", "Samuel Fuller"),
        Quote("Let the audience add up two plus two. They'll love you forever.", "Billy Wilder"),
        Quote("If you have a problem with the third act, the real problem is in the first act.", "Billy Wilder"),
        Quote("Action is character.", "F. Scott Fitzgerald"),
        Quote("You can't wait for inspiration. You have to go after it with a club.", "Jack London"),
        Quote("Start writing, no matter what. The water does not flow until the faucet is turned on.", "Louis L'Amour"),
        Quote("Amateurs sit and wait for inspiration, the rest of us just get up and go to work.", "Stephen King"),
        Quote("If you want to be a writer, you must do two things above all others: read a lot and write a lot.", "Stephen King"),
        Quote("You can always edit a bad page. You can't edit a blank page.", "Jodi Picoult"),
        Quote("When in doubt, have a man come through a door with a gun in his hand.", "Raymond Chandler"),
        Quote("Find out what your hero wants, then just follow him.", "Ray Bradbury"),
        Quote("You fail only if you stop writing.", "Ray Bradbury"),
        Quote("Plot is no more than footprints left in the snow after your characters have run by on their way to incredible destinations.", "Ray Bradbury"),
        Quote("If in the first act you have hung a pistol on the wall, then in the following one it should be fired.", "Anton Chekhov"),
        Quote("Drama is anticipation mingled with uncertainty.", "William Archer"),
        Quote("The difference between the almost right word and the right word is the difference between the lightning bug and the lightning.", "Mark Twain"),
        Quote("Life is a tragedy when seen in close-up, but a comedy in long-shot.", "Charlie Chaplin"),
        Quote("Ideas are like fish. If you want to catch little fish, you can stay in the shallow water. But if you want to catch the big fish, you've got to go deeper.", "David Lynch"),
        Quote("We don't make movies to make money, we make money to make more movies.", "Walt Disney"),
        Quote("It's not what a movie is about, it's how it is about it.", "Roger Ebert"),
        Quote("No good movie is too long and no bad movie is short enough.", "Roger Ebert"),
        Quote("Murder your darlings.", "Arthur Quiller-Couch"),
        Quote("Don't get it right, get it written.", "James Thurber"),
        Quote("True character is revealed in the choices a human being makes under pressure.", "Robert McKee"),
        Quote("Make 'em laugh; make 'em cry; make 'em wait.", "Charles Reade"),
        Quote("Exit, pursued by a bear.", "William Shakespeare, The Winter's Tale"),
        Quote("The first draft of anything is just you telling yourself the story.", "Screenwriting adage"),
        Quote("Write the movie you want to see.", "Screenwriting adage"),
        Quote("Every scene should be a turning point.", "Screenwriting adage"),
        Quote("Enter late, leave early.", "Screenwriting adage"),
        Quote("A page a day is a screenplay in four months.", "Screenwriting adage"),
        Quote("Conflict is drama. Everything else is decoration.", "Screenwriting adage"),
        Quote("Show, don't tell.", "Screenwriting adage"),
    )
}
