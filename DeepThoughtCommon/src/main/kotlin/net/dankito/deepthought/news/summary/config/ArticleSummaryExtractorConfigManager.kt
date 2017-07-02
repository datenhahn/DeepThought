package net.dankito.deepthought.news.summary.config

import net.dankito.data_access.network.webclient.IWebClient
import net.dankito.deepthought.di.CommonComponent
import net.dankito.deepthought.extensions.extractor
import net.dankito.deepthought.model.ArticleSummaryExtractorConfig
import net.dankito.faviconextractor.Favicon
import net.dankito.faviconextractor.FaviconComparator
import net.dankito.faviconextractor.FaviconExtractor
import net.dankito.newsreader.feed.FeedArticleSummaryExtractor
import net.dankito.newsreader.feed.IFeedReader
import net.dankito.newsreader.feed.RomeFeedReader
import net.dankito.newsreader.summary.ImplementedArticleSummaryExtractors
import net.dankito.serializer.ISerializer
import net.dankito.service.data.ArticleSummaryExtractorConfigService
import net.dankito.utils.IThreadPool
import org.slf4j.LoggerFactory
import javax.inject.Inject


class ArticleSummaryExtractorConfigManager(private val webClient: IWebClient, private val configService: ArticleSummaryExtractorConfigService, private val threadPool: IThreadPool) {

    companion object {
        const val MAX_SIZE = 152

        private val log = LoggerFactory.getLogger(ArticleSummaryExtractorConfigManager::class.java)
    }


    private var configurations: MutableMap<String, ArticleSummaryExtractorConfig> = linkedMapOf()

    @Inject
    protected lateinit var faviconExtractor: FaviconExtractor

    @Inject
    protected lateinit var faviconComparator: FaviconComparator

    @Inject
    protected lateinit var serializer: ISerializer

    private var favorites: MutableList<ArticleSummaryExtractorConfig> = ArrayList()

    var isInitialized = false
        private set

    private val initializationListeners = mutableSetOf<() -> Unit>()


    init {
        CommonComponent.component.inject(this)

        configService.dataManager.addInitializationListener {
            readPersistedConfigs()
        }
    }

    private fun readPersistedConfigs() {
        configService.getAllAsync { summaryExtractorConfigs ->
            summaryExtractorConfigs.forEach { config ->
                configurations.put(config.url, config)

                if(config.iconUrl == null) {
                    loadIconAsync(config)
                }
            }

            favorites = configurations.values.filter { it.isFavorite }.sortedBy { it.favoriteIndex }.toMutableList()


            initiallyRetrievedSummaryExtractorConfigs()
        }
    }

    private fun initiallyRetrievedSummaryExtractorConfigs() {
        initImplementedExtractors()

        initAddedExtractors()

        configManagerInitialized()
    }

    private fun initImplementedExtractors() {
        ImplementedArticleSummaryExtractors(webClient).getImplementedExtractors().forEach { implementedExtractor ->
            var config = configurations.get(implementedExtractor.getBaseUrl())

            if (config == null) { // a new, unpersisted ArticleSummaryExtractor
                config = ArticleSummaryExtractorConfig(implementedExtractor.getBaseUrl(), implementedExtractor.getName())
                config.extractor = implementedExtractor
                addConfig(config)
            } else {
                config.extractor = implementedExtractor
            }
        }
    }

    private fun initAddedExtractors() {
        configurations.forEach { (_, config) ->
            if(config.extractor == null) {
                config.extractor = FeedArticleSummaryExtractor(config.url, createFeedReader())
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


    fun getConfigs() : List<ArticleSummaryExtractorConfig> {
        return configurations.values.toList()
    }

    fun getConfig(url: String) : ArticleSummaryExtractorConfig? {
        return configurations[url]
    }


    fun getFavorites(): List<ArticleSummaryExtractorConfig> {
        return favorites.toList()
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

        extractorConfig.favoriteIndex?.let {
            for (i in it..favorites.size - 1) {
                val favorite = favorites.get(i)
                favorite.favoriteIndex?.let { favorite.favoriteIndex = it - 1 }
            }
        }

        extractorConfig.favoriteIndex = null
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
        config.extractor = FeedArticleSummaryExtractor(feedUrl, createFeedReader())

        addConfig(config)

        callback(config)
    }

    private fun createFeedReader(): IFeedReader {
        return RomeFeedReader()
    }

    private fun addConfig(config: ArticleSummaryExtractorConfig) {
        configurations.put(config.url, config)

        saveConfig(config)

        if(config.iconUrl == null) {
            loadIconAsync(config)
        }
    }

    private fun saveConfig(config: ArticleSummaryExtractorConfig) {
        configService.persist(config)
    }

    private fun updateConfig(config: ArticleSummaryExtractorConfig) {
        configService.update(config)
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

}