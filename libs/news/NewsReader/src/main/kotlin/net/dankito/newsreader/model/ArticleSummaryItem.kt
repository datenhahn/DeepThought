package net.dankito.newsreader.model

import net.dankito.deepthought.model.ArticleSummaryExtractorConfig
import net.dankito.newsreader.article.IArticleExtractor
import java.util.*


data class ArticleSummaryItem(val url : String, var title : String, val articleExtractorClass: Class<out IArticleExtractor>?,
                              var summary : String = "", var previewImageUrl : String? = null,
                              var publishedDate: Date? = null, var updatedDate : Date? = null,
                              var articleSummaryExtractorConfig: ArticleSummaryExtractorConfig? = null) {

    private constructor() : this("", "", null) // for Jackson
}