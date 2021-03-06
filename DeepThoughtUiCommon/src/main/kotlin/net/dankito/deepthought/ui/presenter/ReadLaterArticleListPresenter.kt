package net.dankito.deepthought.ui.presenter

import net.dankito.deepthought.data.ItemPersister
import net.dankito.deepthought.di.CommonComponent
import net.dankito.deepthought.model.ReadLaterArticle
import net.dankito.deepthought.ui.IRouter
import net.dankito.deepthought.ui.view.IReadLaterArticleView
import net.dankito.service.data.ReadLaterArticleService
import net.dankito.service.data.messages.EntitiesOfTypeChanged
import net.dankito.service.eventbus.IEventBus
import net.dankito.service.search.ISearchEngine
import net.dankito.service.search.Search
import net.dankito.service.search.specific.ReadLaterArticleSearch
import net.dankito.utils.IThreadPool
import net.dankito.utils.ui.IClipboardService
import net.engio.mbassy.listener.Handler
import javax.inject.Inject


class ReadLaterArticleListPresenter(private val view: IReadLaterArticleView, private val searchEngine: ISearchEngine, private val readLaterArticleService: ReadLaterArticleService,
                                    private val itemPersister: ItemPersister, private val clipboardService: IClipboardService, private val router: IRouter) : IMainViewSectionPresenter {


    @Inject
    protected lateinit var eventBus: IEventBus

    @Inject
    protected lateinit var threadPool: IThreadPool


    private var lastSearchTermProperty = Search.EmptySearchTerm

    private var lastReadLaterArticleSearch: ReadLaterArticleSearch? = null

    private val eventBusListener = EventBusListener()


    init {
        CommonComponent.component.inject(this)
    }


    override fun getLastSearchTerm(): String {
        return lastSearchTermProperty
    }

    private fun getReadLaterArticles() {
        getReadLaterArticles(lastSearchTermProperty)
    }

    fun getReadLaterArticles(searchTerm: String) {
        lastReadLaterArticleSearch?.interrupt()

        lastSearchTermProperty = searchTerm

        lastReadLaterArticleSearch = ReadLaterArticleSearch(searchTerm) {
            view.showEntities(it)
        }

        searchEngine.searchReadLaterArticles(lastReadLaterArticleSearch!!)
    }


    fun deserializeItemExtractionResult(article: ReadLaterArticle) {
        readLaterArticleService.deserializeItemExtractionResult(article)
    }

    fun showArticle(article: ReadLaterArticle) {
        router.showEditItemView(article)
    }

    fun saveAndDeleteReadLaterArticle(article: ReadLaterArticle) {
        itemPersister.saveItemAsync(article.itemExtractionResult) { successful ->
            if(successful) {
                deleteReadLaterArticle(article)
            }
        }
    }

    fun deleteReadLaterArticle(article: ReadLaterArticle?) {
        article?.let {
            readLaterArticleService.delete(it)
        }
    }

    fun copySourceUrlToClipboard(article: ReadLaterArticle) {
        article.sourceUrl?.let { url ->
            clipboardService.copyUrlToClipboard(url)
        }
    }


    override fun viewBecomesVisible() {
        eventBus.register(eventBusListener)
    }

    override fun viewGetsHidden() {
        eventBus.unregister(eventBusListener)
    }


    inner class EventBusListener {

        @Handler
        fun entityChanged(entityChanged: EntitiesOfTypeChanged) {
            if(entityChanged.entityType == ReadLaterArticle::class.java) {
                getReadLaterArticles()
            }
        }

    }

}