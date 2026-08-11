package net.spin.ao3.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.jsoup.nodes.Element

/**
 * Converts sanitized AO3 comment HTML into an [AnnotatedString] with basic
 * inline formatting (bold / italic / underline / links / line breaks). Block
 * elements become paragraph breaks. Falls back to the plain [plainText] when
 * the HTML is empty.
 */
fun htmlToAnnotated(html: String, baseColor: Color, linkColor: Color, plainText: String = ""): AnnotatedString {
    if (html.isBlank()) return AnnotatedString(plainText)
    val body = org.jsoup.Jsoup.parseBodyFragment(html).body()
    return buildAnnotatedString { walkNodes(body, baseColor, linkColor) }
}

private fun AnnotatedString.Builder.walkNodes(el: Element, base: Color, link: Color) {
    el.childNodes().forEach { node ->
        when (node) {
            is org.jsoup.nodes.TextNode -> append(node.text())
            is Element -> when (node.tagName().lowercase()) {
                "b", "strong" -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    walkNodes(node, base, link)
                    pop()
                }
                "i", "em" -> {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    walkNodes(node, base, link)
                    pop()
                }
                "u", "ins" -> {
                    pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                    walkNodes(node, base, link)
                    pop()
                }
                "a" -> {
                    pushStyle(SpanStyle(color = link, textDecoration = TextDecoration.Underline))
                    walkNodes(node, base, link)
                    pop()
                }
                "br" -> append("\n")
                "p", "div", "blockquote", "li", "h1", "h2", "h3", "h4", "h5", "h6", "pre", "center", "section" -> {
                    append("\n")
                    walkNodes(node, base, link)
                    append("\n")
                }
                else -> walkNodes(node, base, link)
            }
            else -> Unit
        }
    }
}
