package com.veronezzi.colaeleitoral.ui.screens.about

import com.veronezzi.colaeleitoral.ui.screens.about.PolicyInline.Code
import com.veronezzi.colaeleitoral.ui.screens.about.PolicyInline.Link
import com.veronezzi.colaeleitoral.ui.screens.about.PolicyInline.Plain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** S6: the Markdown subset of docs/privacidade.md (agreed with the docs agent) renders in full. */
class PolicyMarkdownTest {
    @Test
    fun `title, sections, paragraphs and lists with continuation lines`() {
        val blocks = PolicyMarkdown.parse(
            """
            # Política do app

            Primeira linha
            continua o parágrafo.

            ## 1. Seção **um**

            - Item um
              continua o item.
            - Item dois
            Texto depois da lista.
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                PolicyBlock.Title("Política do app"),
                PolicyBlock.Paragraph(listOf(Plain("Primeira linha continua o parágrafo."))),
                PolicyBlock.Heading("1. Seção um"),
                PolicyBlock.BulletList(listOf(listOf(Plain("Item um continua o item.")), listOf(Plain("Item dois")))),
                PolicyBlock.Paragraph(listOf(Plain("Texto depois da lista."))),
            ),
            blocks,
        )
    }

    @Test
    fun `bold, code, links and plain e-mail addresses`() {
        assertEquals(
            listOf(
                Plain("O app só usa "),
                Code("tse.jus.br", bold = true),
                Plain(": ", bold = true),
                Plain("veja "),
                Link("https://www.gov.br/anpd", "https://www.gov.br/anpd"),
                Plain(" ou escreva para "),
                Link("contato@example.com", "mailto:contato@example.com"),
                Plain("."),
            ),
            PolicyMarkdown.parseInline("O app só usa **`tse.jus.br`: **veja <https://www.gov.br/anpd> ou escreva para contato@example.com."),
        )
    }

    @Test
    fun `unknown markup stays as text, so no sentence is ever dropped`() {
        val blocks = PolicyMarkdown.parse("> citação\n\n| a | b |\n\n`sem fim e <http://inseguro>")
        val text = blocks.filterIsInstance<PolicyBlock.Paragraph>().joinToString("\n") { p -> p.inlines.joinToString("") { it.text } }
        assertEquals("> citação\n| a | b |\n`sem fim e <http://inseguro>", text)
    }

    @Test
    fun `the published policy parses with no markup left and every link clickable`() {
        val blocks = PolicyMarkdown.parse(File(POLICY_PATH).readText())
        assertTrue(blocks.first() is PolicyBlock.Title)
        val headings = blocks.filterIsInstance<PolicyBlock.Heading>().map { it.text }
        assertTrue(headings.toString(), headings.containsAll(listOf("Resumo", "1. Quem é o responsável", "13. Contato")))

        val inlines = blocks.flatMap { block ->
            when (block) {
                is PolicyBlock.Paragraph -> block.inlines
                is PolicyBlock.BulletList -> block.items.flatten()
                else -> emptyList()
            }
        }
        val visible = inlines.joinToString("") { it.text } + headings.joinToString("")
        listOf("**", "`", "<https", "](").forEach { markup -> assertFalse("markup left: $markup", markup in visible) }
        val targets = inlines.filterIsInstance<Link>().map { it.target }
        assertTrue(targets.toString(), targets.all { it.startsWith("https://") || it.startsWith("mailto:") })
        assertTrue(targets.toString(), "mailto:veronezzi14@gmail.com" in targets)
    }

    companion object {
        /** Unit tests run in the app module directory. */
        const val POLICY_PATH = "../docs/privacidade.md"
    }
}
