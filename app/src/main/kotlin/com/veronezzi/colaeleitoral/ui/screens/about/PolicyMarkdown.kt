package com.veronezzi.colaeleitoral.ui.screens.about

/** A run of text inside a paragraph or list item of the privacy policy. */
sealed interface PolicyInline {
    val text: String
    val bold: Boolean

    data class Plain(override val text: String, override val bold: Boolean = false) : PolicyInline

    /** `code`: technical names (hosts, permissions), drawn in a monospace font. */
    data class Code(override val text: String, override val bold: Boolean = false) : PolicyInline

    /** [target] is an `https://` URL or a `mailto:` address; [text] is what the reader sees. */
    data class Link(override val text: String, val target: String, override val bold: Boolean = false) : PolicyInline
}

/** A block of the privacy policy, in document order. */
sealed interface PolicyBlock {
    /** `# ` line: the document title. */
    data class Title(val text: String) : PolicyBlock

    /** `## ` line: a section. */
    data class Heading(val text: String) : PolicyBlock

    data class Paragraph(val inlines: List<PolicyInline>) : PolicyBlock

    /** Consecutive `- ` items (never nested). */
    data class BulletList(val items: List<List<PolicyInline>>) : PolicyBlock
}

/**
 * Parser for the Markdown subset of docs/privacidade.md, the single source of the policy shown in
 * the app and published on the web: `#` title, `##` sections, paragraphs (consecutive lines, a
 * blank line ends the block), `- ` lists with 2-space continuation lines, `**bold**`, `` `code` ``
 * and `<https://...>` links. Plain-text e-mail addresses become `mailto:` links. Anything else
 * (tables, quotes, HTML) is kept as plain text, so no sentence of the policy is ever dropped.
 */
object PolicyMarkdown {
    private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
    private val LINK = Regex("""<(https://[^\s<>]+)>""")
    private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}""")

    fun parse(markdown: String): List<PolicyBlock> {
        val blocks = mutableListOf<PolicyBlock>()
        val paragraph = mutableListOf<String>()
        val items = mutableListOf<StringBuilder>()

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) blocks += PolicyBlock.Paragraph(parseInline(paragraph.joinToString(" ")))
            paragraph.clear()
        }

        fun flushList() {
            if (items.isNotEmpty()) blocks += PolicyBlock.BulletList(items.map { parseInline(it.toString()) })
            items.clear()
        }

        for (rawLine in markdown.replace("\r\n", "\n").split('\n')) {
            val line = rawLine.trimEnd()
            val heading = HEADING.matchEntire(line)
            when {
                line.isBlank() -> {
                    flushParagraph()
                    flushList()
                }
                heading != null -> {
                    flushParagraph()
                    flushList()
                    val text = plainText(heading.groupValues[2].trim())
                    blocks += if (heading.groupValues[1].length == 1) PolicyBlock.Title(text) else PolicyBlock.Heading(text)
                }
                line.startsWith("- ") -> {
                    flushParagraph()
                    items += StringBuilder(line.removePrefix("- ").trim())
                }
                items.isNotEmpty() && line.startsWith("  ") -> items.last().append(' ').append(line.trim())
                else -> {
                    flushList()
                    paragraph += line.trim()
                }
            }
        }
        flushParagraph()
        flushList()
        return blocks
    }

    /** Inline markup of one block: bold, code, autolinks and e-mail addresses. */
    fun parseInline(text: String): List<PolicyInline> {
        val runs = mutableListOf<PolicyInline>()
        val plain = StringBuilder()
        var bold = false

        fun flushPlain() {
            if (plain.isNotEmpty()) runs += splitEmails(plain.toString(), bold)
            plain.clear()
        }

        var index = 0
        while (index < text.length) {
            val closingCode = if (text[index] == '`') text.indexOf('`', index + 1) else -1
            val link = if (text[index] == '<') LINK.matchAt(text, index) else null
            when {
                text.startsWith("**", index) -> {
                    flushPlain()
                    bold = !bold
                    index += 2
                }
                closingCode > index + 1 -> {
                    flushPlain()
                    runs += PolicyInline.Code(text.substring(index + 1, closingCode), bold)
                    index = closingCode + 1
                }
                link != null -> {
                    flushPlain()
                    val url = link.groupValues[1]
                    runs += PolicyInline.Link(url, url, bold)
                    index = link.range.last + 1
                }
                else -> {
                    plain.append(text[index])
                    index += 1
                }
            }
        }
        flushPlain()
        return runs
    }

    /** The text without markup, for titles and headings. */
    fun plainText(text: String): String = parseInline(text).joinToString("") { it.text }

    private fun splitEmails(text: String, bold: Boolean): List<PolicyInline> {
        val runs = mutableListOf<PolicyInline>()
        var start = 0
        EMAIL.findAll(text).forEach { match ->
            if (match.range.first > start) runs += PolicyInline.Plain(text.substring(start, match.range.first), bold)
            runs += PolicyInline.Link(match.value, "mailto:${match.value}", bold)
            start = match.range.last + 1
        }
        if (start < text.length) runs += PolicyInline.Plain(text.substring(start), bold)
        return runs
    }
}
