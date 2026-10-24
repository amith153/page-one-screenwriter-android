package com.amit.pageone.core

import org.w3c.dom.Element as XmlElement
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Final Draft (.fdx) import / export. Uses the JDK DOM parser (available on Android too). */
object Fdx {

    private val typeMap = mapOf(
        "Scene Heading" to ElementType.SCENE_HEADING,
        "Action" to ElementType.ACTION,
        "General" to ElementType.ACTION,
        "Character" to ElementType.CHARACTER,
        "Parenthetical" to ElementType.PARENTHETICAL,
        "Dialogue" to ElementType.DIALOGUE,
        "Transition" to ElementType.TRANSITION,
        "Shot" to ElementType.SHOT,
        "Lyrics" to ElementType.LYRICS,
        "Cast List" to ElementType.ACTION,
        "New Act" to ElementType.CENTERED,
        "End of Act" to ElementType.CENTERED,
    )

    fun parse(bytes: ByteArray, ids: IdGen): Fountain.Parsed {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = false
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        val doc = f.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        val root = doc.documentElement
        val out = ArrayList<Element>()

        val content = childrenNamed(root, "Content").firstOrNull()
        if (content != null) {
            for (p in childrenNamed(content, "Paragraph")) readParagraph(p, out, ids, dual = false)
        }

        // Title page: collect paragraph texts and make a best guess at the fields.
        var tp = TitlePage()
        val tpNode = childrenNamed(root, "TitlePage").firstOrNull()
        if (tpNode != null) {
            val paras = (tpNode as XmlElement).getElementsByTagName("Paragraph")
            val all = (0 until paras.length).map { val p = paras.item(it) as XmlElement; p.getAttribute("Alignment") to textOf(p).trim() }
                .filter { it.second.isNotEmpty() }
            val centered = all.filter { it.first == "Center" || it.first.isEmpty() }.map { it.second }
            val left = all.filter { it.first == "Left" || it.first == "Full" }.map { it.second }
            val right = all.filter { it.first == "Right" }.map { it.second }
            if (centered.isNotEmpty()) {
                val byIdx = centered.indexOfFirst { it.lowercase().let { l -> l == "by" || l.startsWith("written by") || l.startsWith("screenplay by") || l.startsWith("story by") } }
                // Centered block after the title: alternating "X by" / names lines, then "Based on…"
                val credits = ArrayList<Credit>()
                var k = 1
                var source = ""
                while (k < centered.size) {
                    val line = centered[k]
                    val l = line.lowercase()
                    if (l.startsWith("based on")) { source = centered.drop(k).joinToString(" "); break }
                    if (l == "by" || l.endsWith(" by")) {
                        credits.add(Credit(line, centered.getOrNull(k + 1) ?: "")); k += 2
                    } else {
                        if (credits.isEmpty()) credits.add(Credit("Written by", line)) else source = (source + " " + line).trim()
                        k++
                    }
                }
                tp = TitlePage.simple(centered.first(), "Written by", "", source = source, contact = left.joinToString("\n"), draftDate = right.joinToString(" "))
                    .copy(credits = credits.ifEmpty { listOf(Credit()) })
            }
        }
        return Fountain.Parsed(tp, out)
    }

    private fun readParagraph(p: XmlElement, out: MutableList<Element>, ids: IdGen, dual: Boolean) {
        // A DualDialogue paragraph wraps the two speakers' paragraphs.
        val dd = childrenNamed(p, "DualDialogue").firstOrNull()
        if (dd != null) {
            var seenCharacters = 0
            for (inner in childrenNamed(dd, "Paragraph")) {
                val isChar = inner.getAttribute("Type") == "Character"
                if (isChar) seenCharacters++
                readParagraph(inner, out, ids, dual = isChar && seenCharacters == 2)
            }
            return
        }
        val type = typeMap[p.getAttribute("Type")] ?: ElementType.ACTION
        var text = textOf(p)
        if (type.allCaps) text = text.uppercase()
        var color = 0
        var synopsis = ""
        if (type == ElementType.SCENE_HEADING) {
            val props = childrenNamed(p, "SceneProperties").firstOrNull()
            if (props != null) {
                val syn = childrenNamed(props, "Summary").firstOrNull()
                if (syn != null) synopsis = syn.textContent.trim()
            }
        }
        out.add(Element(ids.next(), type, text, dual = dual, color = color, synopsis = synopsis))
    }

    private fun textOf(p: XmlElement): String {
        val sb = StringBuilder()
        for (t in childrenNamed(p, "Text")) sb.append(t.textContent)
        return sb.toString().replace("\r", "")
    }

    private fun childrenNamed(n: Node, name: String): List<XmlElement> {
        val out = ArrayList<XmlElement>()
        val ch = n.childNodes
        for (k in 0 until ch.length) {
            val c = ch.item(k)
            if (c is XmlElement && c.tagName == name) out.add(c)
        }
        return out
    }

    // ------------------------------------------------------------------ writer

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun fdxType(t: ElementType) = when (t) {
        ElementType.SCENE_HEADING -> "Scene Heading"
        ElementType.ACTION -> "Action"
        ElementType.CHARACTER -> "Character"
        ElementType.PARENTHETICAL -> "Parenthetical"
        ElementType.DIALOGUE -> "Dialogue"
        ElementType.TRANSITION -> "Transition"
        ElementType.SHOT -> "Shot"
        ElementType.LYRICS -> "Lyrics"
        ElementType.CENTERED -> "Action"
        ElementType.NOTE -> "Action"
    }

    fun write(script: Script): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>\n")
        sb.append("<FinalDraft DocumentType=\"Script\" Template=\"No\" Version=\"5\">\n  <Content>\n")
        val els = script.elements.filter { it.type != ElementType.NOTE }
        fun para(e: Element, indent: String) {
            sb.append(indent).append("<Paragraph Type=\"").append(fdxType(e.type)).append("\"")
            if (e.type == ElementType.CENTERED) sb.append(" Alignment=\"Center\"")
            sb.append(">\n")
            if (e.type == ElementType.SCENE_HEADING && e.synopsis.isNotBlank()) {
                sb.append(indent).append("  <SceneProperties><Summary><Paragraph><Text>")
                    .append(esc(e.synopsis)).append("</Text></Paragraph></Summary></SceneProperties>\n")
            }
            sb.append(indent).append("  <Text>").append(esc(e.text)).append("</Text>\n")
            sb.append(indent).append("</Paragraph>\n")
        }
        var i = 0
        while (i < els.size) {
            val e = els[i]
            if (e.type == ElementType.CHARACTER) {
                var end = i + 1
                while (end < els.size && els[end].type in setOf(ElementType.DIALOGUE, ElementType.PARENTHETICAL, ElementType.LYRICS)) end++
                if (end < els.size && els[end].type == ElementType.CHARACTER && els[end].dual) {
                    var end2 = end + 1
                    while (end2 < els.size && els[end2].type in setOf(ElementType.DIALOGUE, ElementType.PARENTHETICAL, ElementType.LYRICS)) end2++
                    sb.append("    <Paragraph>\n      <DualDialogue>\n")
                    for (k in i until end2) para(els[k], "        ")
                    sb.append("      </DualDialogue>\n    </Paragraph>\n")
                    i = end2
                    continue
                }
            }
            para(e, "    ")
            i++
        }
        sb.append("  </Content>\n")
        val tp = script.titlePage
        sb.append("  <TitlePage>\n    <Content>\n")
        fun tpPara(text: String, align: String) {
            for (line in text.split('\n')) {
                sb.append("      <Paragraph Alignment=\"").append(align).append("\" Type=\"General\"><Text>")
                    .append(esc(line)).append("</Text></Paragraph>\n")
            }
        }
        repeat(14) { tpPara("", "Center") }
        tpPara(tp.title.ifBlank { script.title }.uppercase(), "Center")
        tpPara("", "Center")
        tp.credits.filter { it.names.isNotBlank() }.forEach { c ->
            tpPara(c.role, "Center"); tpPara("", "Center"); tpPara(c.names, "Center"); tpPara("", "Center")
        }
        if (tp.basedOnLine.isNotBlank()) { tpPara(tp.basedOnLine, "Center") }
        repeat(12) { tpPara("", "Left") }
        if (tp.contactLines.isNotEmpty()) tpPara(tp.contactLines.joinToString("\n"), "Left")
        listOf(tp.draft, tp.draftDate, tp.copyright, tp.registration).filter { it.isNotBlank() }.forEach { tpPara(it, "Right") }
        sb.append("    </Content>\n  </TitlePage>\n</FinalDraft>\n")
        return sb.toString()
    }
}
