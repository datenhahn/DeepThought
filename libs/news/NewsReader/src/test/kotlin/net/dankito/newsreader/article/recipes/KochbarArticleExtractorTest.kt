package net.dankito.newsreader.article.recipes

import net.dankito.data_access.network.webclient.IWebClient
import net.dankito.newsreader.article.ArticleExtractorTestBase
import net.dankito.newsreader.article.IArticleExtractor
import org.junit.Test

class KochbarArticleExtractorTest : ArticleExtractorTestBase() {

    override fun createArticleExtractor(webClient: IWebClient): IArticleExtractor {
        return KochbarArticleExtractor(webClient)
    }


    @Test
    fun importJoghurtMaracujaTorteRecipe() {
        getAndTestArticle("https://www.kochbar.de/rezept/528990/Joghurt-Maracuja-Torte.html",
                "Joghurt-Maracuja-Torte",
                "-frisch-fruchtig-fluffig-locker-leicht-",
                null, 7600, true)
    }

}