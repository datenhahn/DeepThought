package net.dankito.deepthought.news.summary.config

import net.dankito.deepthought.di.CommonComponent
import net.dankito.deepthought.extensions.extractor
import net.dankito.deepthought.model.ArticleSummaryExtractorConfig
import net.dankito.faviconextractor.Favicon
import net.dankito.faviconextractor.FaviconComparator
import net.dankito.faviconextractor.FaviconExtractor
import net.dankito.newsreader.feed.FeedArticleSummaryExtractor
import net.dankito.newsreader.feed.IFeedReader
import net.dankito.newsreader.summary.IImplementedArticleSummaryExtractorsManager
import net.dankito.service.data.ArticleSummaryExtractorConfigService
import net.dankito.service.data.messages.ArticleSummaryExtractorConfigChanged
import net.dankito.service.data.messages.EntityChangeSource
import net.dankito.service.eventbus.EventBusPriorities
import net.dankito.service.eventbus.IEventBus
import net.engio.mbassy.listener.Handler
import javax.inject.Inject
import kotlin.concurrent.thread


class ArticleSummaryExtractorConfigManager(private val extractorManager: IImplementedArticleSummaryExtractorsManager, private val configService: ArticleSummaryExtractorConfigService,
                                           private val feedReader: IFeedReader) {

    companion object {
        const val MAX_SIZE = 196
    }


    private var configurations: MutableMap<String, ArticleSummaryExtractorConfig> = linkedMapOf()

    @Inject
    protected lateinit var faviconExtractor: FaviconExtractor

    @Inject
    protected lateinit var faviconComparator: FaviconComparator

    @Inject
    protected lateinit var eventBus: IEventBus

    private var favorites: MutableList<ArticleSummaryExtractorConfig> = ArrayList()

    private var highestSortOrder = -1

    var isInitialized = false
        private set

    private val initializationListeners = mutableSetOf<() -> Unit>()

    private val eventBusListener = EventBusListener()


    init {
        CommonComponent.component.inject(this)

        configService.dataManager.addInitializationListener {
            thread {
                readPersistedConfigs()
                initiallyRetrievedSummaryExtractorConfigs()
            }
        }

        eventBus.register(eventBusListener)
    }

    private fun readPersistedConfigs() { // for EventBusListener ArticleSummaryExtractorConfigs have to be retrieved synchronous so that all other listeners are aware of changes done in this method
        val summaryExtractorConfigs = configService.getAll()

        configurations.clear()
        favorites.clear()

        summaryExtractorConfigs.forEach { config ->
            configurations.put(config.url, config)

            if(config.iconUrl == null) {
                loadIconAsync(config)
            }

            if(config.sortOrder > highestSortOrder) {
                highestSortOrder = config.sortOrder
            }
        }
    }

    private fun initiallyRetrievedSummaryExtractorConfigs() {
        configurateExtractors()

        configManagerInitialized()
    }

    private fun configurateExtractors() {
        initImplementedExtractors()

        initFeedExtractors()

        handleConfigsWithoutExtractors()

        determineFavorites()
    }

    private fun initImplementedExtractors() {
        extractorManager.getImplementedExtractors().forEach { implementedExtractor ->
            var config = configurations.get(implementedExtractor.getUrl())

            if(config == null) { // a new, unpersisted ArticleSummaryExtractor
                config = ArticleSummaryExtractorConfig(implementedExtractor.getUrl(), implementedExtractor.getName())
                config.extractor = implementedExtractor
                addConfig(config)
            }
            else {
                config.extractor = implementedExtractor
            }
        }
    }

    private fun initFeedExtractors() {
        configurations.forEach { (_, config) ->
            if(config.siteUrl != null) { // currently only Feeds have siteUrl set
                config.extractor = FeedArticleSummaryExtractor(config.url, feedReader)
            }
        }
    }

    /**
     * Handles ArticleSummaryExtractorConfig which's ArticleSummaryExtractor hasn't been found (e.g. a synchronized device knows this extractor but we don't)
     */
    private fun handleConfigsWithoutExtractors() {
        ArrayList(configurations.values).forEach { config ->
            if(config.extractor == null) { // currently only Feed have siteUrl set
                configurations.remove(config.url)
            }
        }
    }

    private fun loadIconAsync(config: ArticleSummaryExtractorConfig) {
        loadIconAsync(config.url) { bestIconUrl ->
            bestIconUrl?.let {
                config.iconUrl = it
                updateConfig(config)
            }
        }
    }

    private fun loadIconAsync(url: String, callback: (String?) -> Unit)  {
        faviconExtractor.extractFaviconsAsync(url) {
            if(it.result != null) {
                callback(faviconComparator.getBestIcon(it.result as List<Favicon>, maxSize = MAX_SIZE, returnSquarishOneIfPossible = true)?.url)
            }
            else {
                callback(null)
            }
        }
    }

    private fun determineFavorites() {
        favorites = configurations.values.filter { it.isFavorite }.sortedBy { it.favoriteIndex }.toMutableList()
    }


    private fun articleSummaryExtractorConfigUpdated() {
        readPersistedConfigs()
        configurateExtractors()
    }


    fun getConfigs() : List<ArticleSummaryExtractorConfig> {
        return configurations.values.sortedBy { it.sortOrder }
    }

    fun getConfig(url: String) : ArticleSummaryExtractorConfig? {
        return configurations[url]
    }


    fun getFavorites(): List<ArticleSummaryExtractorConfig> {
        return favorites.toList()
    }

    fun toggleFavoriteStatus(extractorConfig: ArticleSummaryExtractorConfig) {
        setFavoriteStatus(extractorConfig, !extractorConfig.isFavorite)
    }

    fun setFavoriteStatus(extractorConfig: ArticleSummaryExtractorConfig, isFavorite: Boolean) {
        extractorConfig.isFavorite = isFavorite

        if(isFavorite) {
            extractorConfig.favoriteIndex = favorites.size
            favorites.add(extractorConfig)
        }
        else {
            removeFavorite(extractorConfig)
        }

        updateConfig(extractorConfig)
    }

    private fun removeFavorite(extractorConfig: ArticleSummaryExtractorConfig) {
        favorites.remove(extractorConfig)

        extractorConfig.favoriteIndex = null

        for(i in 0..favorites.size - 1) {
            val favorite = favorites.get(i)
            val indexBefore = favorite.favoriteIndex

            favorite.favoriteIndex = i

            if(indexBefore != favorite.favoriteIndex) {
                updateConfig(favorite)
            }
        }
    }


    fun addFeed(feedUrl: String, config: ArticleSummaryExtractorConfig, callback: (ArticleSummaryExtractorConfig?) -> Unit) {
        if(config.iconUrl != null) {
            addFeed(feedUrl, config, config.iconUrl, callback)
        }
        else {
            loadIconAsync(feedUrl) {
                addFeed(feedUrl, config, it, callback)
            }
        }
    }

    private fun addFeed(feedUrl: String, config: ArticleSummaryExtractorConfig, iconUrl: String?, callback: (ArticleSummaryExtractorConfig?) -> Unit) {
        config.iconUrl = iconUrl
        config.extractor = FeedArticleSummaryExtractor(feedUrl, feedReader)

        addConfig(config)

        callback(config)
    }

    private fun addConfig(config: ArticleSummaryExtractorConfig) {
        config.sortOrder = ++highestSortOrder

        configurations.put(config.url, config)

        saveConfig(config)

        if(config.iconUrl == null) {
            loadIconAsync(config)
        }
    }

    fun configurationUpdated(config: ArticleSummaryExtractorConfig) {
        updateConfig(config)
    }

    private fun saveConfig(config: ArticleSummaryExtractorConfig) {
        configService.persist(config)
    }

    private fun updateConfig(config: ArticleSummaryExtractorConfig) {
        configService.update(config)
    }


    fun deleteConfig(config: ArticleSummaryExtractorConfig) {
        configurations.remove(config.url)

        removeFavorite(config)

        configService.delete(config)
    }


    fun addInitializationListener(listener: () -> Unit) {
        if(isInitialized) {
            callInitializationListener(listener)
        }
        else {
            initializationListeners.add(listener)
        }
    }

    private fun configManagerInitialized() {
        isInitialized = true

        for(listener in HashSet<() -> Unit>(initializationListeners)) {
            callInitializationListener(listener)
        }

        initializationListeners.clear()
    }

    private fun callInitializationListener(listener: () -> Unit) {
        listener()
    }


    inner class EventBusListener {

        @Handler(priority = EventBusPriorities.EntityService)
        fun configChanged(change: ArticleSummaryExtractorConfigChanged) {
            if(change.source == EntityChangeSource.Synchronization) {
                articleSummaryExtractorConfigUpdated()
            }
        }

    }

}