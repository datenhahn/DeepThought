package net.dankito.newsreader.article

import net.dankito.utils.web.client.IWebClient
import net.dankito.deepthought.model.Item
import net.dankito.deepthought.model.Source
import net.dankito.deepthought.model.util.ItemExtractionResult
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.text.SimpleDateFormat
import java.util.*


class PostillonArticleExtractor(webClient: IWebClient) : ArticleExtractorBase(webClient) {

    companion object {
        private val PostillionDateFormat = SimpleDateFormat("EEEE, dd. MMMMM yyyy", Locale.GERMAN)
    }


    override fun getName(): String? {
        return "Postillon"
    }

    override fun canExtractItemFromUrl(url: String): Boolean {
        return isHttpOrHttpsUrlFromHost(url, "www.der-postillon.com/")
    }


    override fun parseHtmlToArticle(extractionResult: ItemExtractionResult, document: Document, url: String) {
        document.body().select(".post").first()?.let { postElement ->
            postElement.select(".post-title")?.let { titleElement ->
                postElement.select(".post-body").first()?.let { bodyElement ->
                    val item = Item(extractContent(bodyElement))

                    val source = Source(titleElement.text(), url, extractPublishingDate(postElement))

                    bodyElement.select(".separator a img").first()?.let { source.previewImageUrl = it.attr("src") }

                    extractionResult.setExtractedContent(item, source)
                }
            }
        }
    }

    private fun extractContent(bodyElement: Element): String {
        return bodyElement.childNodes().filter { shouldFilterNode(it) == false }.joinToString(separator = "") { getTextRepresentationForContentNode(it) }
    }

    private fun getTextRepresentationForContentNode(node: Node): CharSequence {
        return if ("br" == node.nodeName()) {
            "</p><p>"
        }
        else if (node is TextNode) {
            node.text()
        }
        else if ("div" == node.nodeName() && (node as Element).hasClass("separator")) {
            node.outerHtml() + "<p>"
        }
        else if(isSonntagsfrageElement(node)) {
            extractSonntagsfrageHtml(node as Element)
        }
        else {
            node.outerHtml()
        }
    }

    private fun isSonntagsfrageElement(node: Node): Boolean {
        return node is Element && node.text().contains("(Direktlink zur Umfrage)")
    }

    private fun extractSonntagsfrageHtml(sonntagsFrageElement: Element): String {
        sonntagsFrageElement.select("noscript a").first()?.let {
            val pollDaddyLink = it.attr("href")

            val iframe = Element("iframe")
            iframe.attr("src", pollDaddyLink)
            iframe.attr("height", "600")
            iframe.attr("width", "100%")

            it.parentNode().replaceWith(iframe)

            return sonntagsFrageElement.html()
        }

        return ""
    }

    private fun shouldFilterNode(node: Node) : Boolean {
        return (node is TextNode && node.isBlank) || node.attr("id") == "narrando_mobil" || ("a" == node.nodeName() && "more" == (node as Element).attr("name"))
    }

    private fun extractPublishingDate(postElement: Element): Date? {
        postElement.select(".date-header span").first()?.let {
            return PostillionDateFormat.parse(it.text())
        }

        return null
    }

}