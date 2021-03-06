package net.dankito.newsreader.article

import net.dankito.deepthought.model.Item
import net.dankito.deepthought.model.Source
import net.dankito.deepthought.model.util.ItemExtractionResult
import net.dankito.utils.web.client.IWebClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


class SueddeutscheArticleExtractor(webClient: IWebClient) : ArticleExtractorBase(webClient) {

    companion object {
        val SueddeutscheHeaderDateFormat: DateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    }


    private var triedToResolveMultiPageArticle = false


    override fun getName(): String? {
        return "SZ"
    }

    override fun canExtractItemFromUrl(url: String): Boolean {
        return isHttpOrHttpsUrlFromHost(url, "www.sueddeutsche.de/")
    }


    override fun extractArticle(url: String): ItemExtractionResult? {
        var siteUrl = url
        if(siteUrl.contains("?reduced=true")) {
            siteUrl = siteUrl.replace("?reduced=true", "")
        }

        return super.extractArticle(siteUrl)
    }

    override fun parseHtmlToArticle(extractionResult: ItemExtractionResult, document: Document, url: String) {
        if(isMultiPageArticle(document) && triedToResolveMultiPageArticle == false) { // some multi page articles after fetching read all on one page still have the read all on  page link
            triedToResolveMultiPageArticle = true
            extractArticleWithPost(extractionResult, url, "article.singlePage=true") // -> extractArticleWithPost() would be called endlessly. that's what triedToResolveMultiPageArticle is there for to avoid this
            return
        }

        triedToResolveMultiPageArticle = false

        document.body().select("#sitecontent").first()?.let { siteContent ->
            extractArticle(extractionResult, siteContent, url)
            return
        }

        document.body().select("article.gallery").first()?.let { galleryArticleElement ->
            extractGalleryArticle(extractionResult, galleryArticleElement, url)
        }
    }

    private fun isMultiPageArticle(document: Document): Boolean {
        return document.body().getElementById("singlePageForm") != null
    }


    private fun extractArticle(extractionResult: ItemExtractionResult, siteContent: Element, siteUrl: String) {
        val source = extractSource(siteContent, siteUrl)

        siteContent.select("#article-body").first()?.let { articleBody ->
            var content = loadLazyLoadingElementsAndGetContent(siteContent, articleBody)

            extractTopEnrichment(siteContent, source, siteUrl)?.let { topEnrichment ->
                content = "<div>" + topEnrichment.outerHtml() + "</div>" + content
            }

            val item = Item(content)

            extractionResult.setExtractedContent(item, source)
        }
    }

    private fun extractTopEnrichment(siteContent: Element, source: Source?, siteUrl: String): Element? {
        siteContent.select(".topenrichment").first()?.let { topEnrichment ->
            topEnrichment.select("figure img").first()?.let { previewImage ->
                val previewImageUrl = getLazyLoadingOrNormalUrlAndMakeLinkAbsolute(previewImage, "src", siteUrl)
                source?.previewImageUrl = previewImageUrl
                previewImage.attr("src", previewImageUrl)

                return previewImage
            }

            topEnrichment.select(".enrichment-inline-video").first()?.let { previewVideo ->
                return previewVideo
            }
        }

        return null
    }

    private fun loadLazyLoadingElementsAndGetContent(siteContent: Element, articleBody: Element): String {
        extractInlineGalleries(articleBody)
        extractInlineCarousels(articleBody)

        cleanArticleBody(articleBody)

        super.loadLazyLoadingElements(articleBody)

        var content = articleBody.html()

        siteContent.select(".topenrichment").first()?.let { topEnrichment ->
            topEnrichment.select("iframe").first()?.let { topEnrichmentIFrame ->
                loadLazyLoadingElement(topEnrichmentIFrame)
                content = topEnrichment.outerHtml() + content
            }
        }

        return content
    }

    private fun cleanArticleBody(articleBody: Element) {
        articleBody.select("#article-sidebar-wrapper, #sharingbaranchor, .ad, .authors, .teaserable-layout, .flexible-teaser, #iq-artikelanker, [data-poll]").remove()

        // remove scripts with try{window.performance.mark('monitor_articleTeaser');}catch(e){};
        articleBody.select("script").filter { it.html().contains("window.performance.mark") }.forEach { it.remove() }

        articleBody.select(".asset-infobox").filter { it.html().contains("Interview am Morgen") }.forEach { it.remove() }

        removeBaseBoxes(articleBody)

        showSZPlusFrame(articleBody)
    }

    private fun removeBaseBoxes(articleBody: Element) {
        articleBody.select("div.basebox").forEach { baseBox ->
            baseBox.select("script").first()?.let { script ->
                if(script.attr("src").startsWith("https://cdn-rawr-production.global.ssl.fastly.net/api/v2/embed/rawr/")) { // a survey
                    baseBox.remove()
                }
            }

            baseBox.select("iframe").forEach { iframe ->
                val dataSrc = iframe.attr("data-src")

                if(dataSrc.startsWith("http://www.nl-services.com/subscribe/") || dataSrc.contains("/sueddeutsche-zeitung/subscribe/") || // subscribe to newsletter
                        dataSrc.startsWith("https://widget.whatsbroadcast.com/")) { // get notified via WhatsApp
                    tryToRemoveWhatsAppPrivacyPolicyNotification(baseBox) // has to be done before removing baseBox element

                    baseBox.remove()
                }
            }
        }
    }

    private fun tryToRemoveWhatsAppPrivacyPolicyNotification(element: Element) {
        var nextSibling = element.nextElementSibling()

        while(nextSibling != null) {
            if(nextSibling.text().contains("Genaue Informationen, welche Daten für den Messenger-Dienst genutzt und gespeichert werden, finden Sie in der " +
                    "Datenschutzerklärung.")) {
                nextSibling.remove()
            }

            nextSibling = nextSibling.nextElementSibling()
        }
    }

    private fun showSZPlusFrame(articleBody: Element) {
        articleBody.select(".opc-placeholder").first()?.let { szPlusPlaceHolder ->
            var dataBind = szPlusPlaceHolder.attr("data-bind")
            dataBind = dataBind.replace("&quot;", "\"")

            val urlStartIndex = dataBind.indexOf("url\": \"") + "url\": \"".length
            if (urlStartIndex > 0) {
                var url = dataBind.substring(urlStartIndex)
                url = url.substring(0, url.indexOf('\"'))

                val iframeElement = Element("iframe")
                iframeElement.attr("src", url)
                szPlusPlaceHolder.replaceWith(iframeElement)
            }
        }
    }

    private fun extractInlineGalleries(articleBody: Element) {
        articleBody.select("figure.gallery.inline").forEach { inlineGallery ->
            inlineGallery.select(".navigation").remove()

            inlineGallery.select("li").forEach { imageListElement ->
                imageListElement.remove()
                inlineGallery.append("<p>" + imageListElement.html() + "</p>")
            }
        }
    }

    private fun extractInlineCarousels(articleBody: Element) {
        articleBody.select("figure.js-biga").forEach { inlineCarousel ->
            var childIndex = inlineCarousel.siblingIndex()

            inlineCarousel.select("ul.biga__carousel__list li.js-carousel-item").forEach { carouselItem ->
                val img = carouselItem.select("img.biga__carousel__list__item-image").first()
                // may also add caption: div.biga__carousel__list__item-source
                inlineCarousel.parent().insertChildren(childIndex, Arrays.asList(img))
                childIndex = childIndex + 2
            }

            inlineCarousel.remove()
        }
    }


    private fun extractGalleryArticle(extractionResult: ItemExtractionResult, galleryArticleElement: Element, siteUrl: String) {
        val source = extractSource(galleryArticleElement, siteUrl)

        galleryArticleElement.select("#article-body").first()?.let { articleBody ->
            val content = StringBuilder()

            // try to read publishing date first as readHtmlOfAllImagesInGallery() removes it
            articleBody.select(".offscreen").first()?.let {
                source?.setPublishingDate(parseSueddeutscheDateString(it.text()), it.text())
            }

            readHtmlOfAllImagesInGallery(content, articleBody, siteUrl)

            extractionResult.setExtractedContent(Item(content.toString()), source)
        }
    }

    private fun readHtmlOfAllImagesInGallery(imageHtml: StringBuilder, articleBody: Element, currentImageUrl: String) {
        articleBody.select("img").first()?.let {
            imageHtml.append(loadLazyLoadingElement(it).outerHtml())
        }

        articleBody.select(".caption").first()?.let { caption ->
            caption.select("#article-sidebar-wrapper, .article-sidebar-wrapper, .authors, .date-copy").remove()
            imageHtml.append("<p>" + caption.html() + "</p>")
        }

        getUrlOfNextImageInGallery(articleBody, currentImageUrl)?.let { nextImageUrl ->
            if(imageUrlAlreadyLoaded(currentImageUrl, nextImageUrl, imageHtml) == false) {
                // otherwise image gallery starts over again with first image -> would cause an infinite loop
                readHtmlOfAllImagesInGallery(imageHtml, nextImageUrl)
            }
        }
    }

    private fun imageUrlAlreadyLoaded(currentImageUrl: String, nextImageUrl: String, imageHtml: StringBuilder): Boolean {
        return currentImageUrl.contains(nextImageUrl) || imageHtml.contains(nextImageUrl)
    }

    private fun getUrlOfNextImageInGallery(articleBody: Element, siteUrl: String): String? {
        var url = articleBody.select("a.next").first()?.attr("href")

        url?.let {  notNullUrl ->
            url = makeLinkAbsolute(notNullUrl, siteUrl)
        }

        return url
    }

    private fun readHtmlOfAllImagesInGallery(imageHtml: StringBuilder, nextImageUrl: String) {
        try {
            requestUrl(nextImageUrl).let { document ->
                document.body().select("#article-body > figure").first()?.let { articleBody ->
                    readHtmlOfAllImagesInGallery(imageHtml, articleBody, nextImageUrl)
                }
            }
        } catch (e: Exception) {
//            log.error("Could not extract html of next image in gallery from url " + nextImageUrl, e)
        }
    }


    private fun extractSource(articleElement: Element, url: String): Source? {
        articleElement.select(".header").first()?.let { headerElement ->
            headerElement.select("h2, h1").first()?.let { heading -> // don't know why, but sometimes (on mobile sites?) they're using h1
                var subTitle = ""
                heading.select("strong").first()?.let {
                    subTitle = it.text()
                    it.remove() // remove element so that it's not as well part of title
                }

                val publishingDate = extractPublishingDate(headerElement)

                return Source(heading.text(), url, publishingDate, subTitle = subTitle)
            }
        }

        return null
    }

    private fun extractPublishingDate(headerElement: Element): Date? {
        headerElement.select("time").first()?.let {
            try {
                val dateString = it.attr("datetime")
                return parseSueddeutscheDateString(dateString)
            } catch(ignored: Exception) { }
        }

        return null
    }

    private fun parseSueddeutscheDateString(dateString: String) : Date? {
        try {
            return SueddeutscheHeaderDateFormat.parse(dateString)
        } catch(ignored: Exception) { }

        return null
    }

}