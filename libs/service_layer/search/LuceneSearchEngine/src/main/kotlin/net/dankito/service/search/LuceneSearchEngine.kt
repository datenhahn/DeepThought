package net.dankito.service.search

import net.dankito.deepthought.model.*
import net.dankito.deepthought.service.data.DataManager
import net.dankito.service.data.*
import net.dankito.service.eventbus.IEventBus
import net.dankito.service.search.specific.*
import net.dankito.service.search.writerandsearcher.*
import net.dankito.utils.IThreadPool
import net.dankito.utils.language.ILanguageDetector
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*
import kotlin.concurrent.schedule
import kotlin.concurrent.thread


class LuceneSearchEngine(private val dataManager: DataManager, private val languageDetector: ILanguageDetector, threadPool: IThreadPool, eventBus: IEventBus, entryService: EntryService,
                         tagService: TagService, referenceService: ReferenceService, seriesService: SeriesService, readLaterArticleService: ReadLaterArticleService)
    : SearchEngineBase(threadPool) {

    companion object {
        private const val DefaultDelayBeforeUpdatingIndexSeconds = 60

        private val log = LoggerFactory.getLogger(LuceneSearchEngine::class.java)
    }


    private val entryIndexWriterAndSearcher = EntryIndexWriterAndSearcher(entryService, eventBus, threadPool)

    private val tagIndexWriterAndSearcher = TagIndexWriterAndSearcher(tagService, eventBus, threadPool, entryIndexWriterAndSearcher)

    private val referenceIndexWriterAndSearcher = ReferenceIndexWriterAndSearcher(referenceService, eventBus, threadPool)

    private val seriesIndexWriterAndSearcher = SeriesIndexWriterAndSearcher(seriesService, eventBus, threadPool)

    private val readLaterArticleIndexWriterAndSearcher = ReadLaterArticleIndexWriterAndSearcher(readLaterArticleService, eventBus, threadPool)

    private val indexWritersAndSearchers: List<IndexWriterAndSearcher<*>>

    private lateinit var defaultIndexDirectory: Directory


    init {
        indexWritersAndSearchers = listOf(entryIndexWriterAndSearcher, tagIndexWriterAndSearcher, referenceIndexWriterAndSearcher, seriesIndexWriterAndSearcher, readLaterArticleIndexWriterAndSearcher)

        createDirectoryAndIndexSearcherAndWritersAsync()
    }


    private fun createDirectoryAndIndexSearcherAndWritersAsync() {
        thread(priority = Thread.MAX_PRIORITY) { initializeDirectoriesAndIndexSearcherAndWriters() }
    }

    private fun initializeDirectoriesAndIndexSearcherAndWriters() {
        try {
            val indexBaseDir = File(dataManager.dataFolderPath, "index")
            val indexDirExists = indexBaseDir.exists()

            val defaultIndexDirectoryFile = File(indexBaseDir, "default")
            defaultIndexDirectory = FSDirectory.open(defaultIndexDirectoryFile)

            for(writerAndSearcher in indexWritersAndSearchers) {
                writerAndSearcher.createDirectory(indexBaseDir)
            }

            initializeIndexSearchersAndWriters()

            if(indexDirExists == false) {
                // TODO: inform user that index is going to be rebuilt and that this takes some time
                rebuildIndex() // do not rebuild index asynchronously as Application depends on some functions of SearchEngine (like Entries without Tags)
            }

            searchEngineInitialized()
        } catch (ex: Exception) {
            log.error("Could not open Lucene Index Directory" , ex)
        }
    }

    private fun initializeIndexSearchersAndWriters() {
        val defaultAnalyzer = LanguageDependentAnalyzer(languageDetector)

        for(writerAndSearcher in indexWritersAndSearchers) {
            writerAndSearcher.initialize(defaultAnalyzer)
        }
    }

    override fun searchEngineInitialized() {
        super.searchEngineInitialized()

        Timer().schedule(DefaultDelayBeforeUpdatingIndexSeconds * 1000L) {
            updateIndex()
        }
    }

    /**
     * There are many reasons why the index isn't in sync anymore with database (e.g. an entity got synchronized but app gets closed before it gets indexed).
     * This methods (re-)indexes all entities with changes since dataManager.localSettings.lastSearchIndexUpdateTime to ensure index is up to date again
     */
    private fun updateIndex() {
        val startTime = Date()
        log.info("Starting updating index with last index update time ${dataManager.localSettings.lastSearchIndexUpdateTime}")

        dataManager.entityManager.getAllEntitiesUpdatedAfter<BaseEntity>(dataManager.localSettings.lastSearchIndexUpdateTime).forEach { entity ->
            updateEntityInIndex(entity)
        }

        dataManager.localSettings.lastSearchIndexUpdateTime = startTime
        dataManager.localSettingsUpdated()

        log.info("Done updating index")
    }

    private fun updateEntityInIndex(entity: BaseEntity) {
        if(entity is Entry) {
            entryIndexWriterAndSearcher.updateEntityInIndex(entity)
        }
        else if(entity is Tag) {
            tagIndexWriterAndSearcher.updateEntityInIndex(entity)
        }
        else if(entity is Series) {
            seriesIndexWriterAndSearcher.updateEntityInIndex(entity)
        }
        else if(entity is Reference) {
            referenceIndexWriterAndSearcher.updateEntityInIndex(entity)
        }
        else if(entity is ReadLaterArticle) {
            readLaterArticleIndexWriterAndSearcher.updateEntityInIndex(entity)
        }
    }


    override fun close() {
        for(writerAndSearcher in indexWritersAndSearchers) {
            writerAndSearcher.close()
        }
    }


    private fun rebuildIndex() {
        deleteIndex()

        log.info("Going to rebuild Lucene index ...")

        indexWritersAndSearchers.forEach { it.indexAllEntities() }

        log.info("Done rebuilding Lucene Index.");
    }

    /**
     *
     *
     * Deletes complete Lucene index.
     * We hope you know what you are doing.
     *
     */
    private fun deleteIndex() {
        log.info("Going to delete Lucene Index ...")

        try {
            for(writerAndSearcher in indexWritersAndSearchers) {
                writerAndSearcher.deleteIndex()
            }

            log.info("Lucene Index successfully deleted")
        } catch (ex: Exception) {
            log.error("Could not delete Lucene index", ex)
        }
    }


    /*      ISearchEngine implementation        */

    override fun searchEntries(search: EntriesSearch, termsToSearchFor: List<String>) {
        entryIndexWriterAndSearcher.searchEntries(search, termsToSearchFor)
    }

    override fun searchTags(search: TagsSearch, termsToSearchFor: List<String>) {
        tagIndexWriterAndSearcher.searchTags(search, termsToSearchFor)
    }

    override fun searchFilteredTags(search: FilteredTagsSearch, termsToSearchFor: List<String>) {
        tagIndexWriterAndSearcher.searchFilteredTags(search, termsToSearchFor)
    }

    override fun searchReferences(search: ReferenceSearch, termsToSearchFor: List<String>) {
        referenceIndexWriterAndSearcher.searchReferences(search, termsToSearchFor)
    }

    override fun searchSeries(search: SeriesSearch, termsToSearchFor: List<String>) {
        seriesIndexWriterAndSearcher.searchSeries(search, termsToSearchFor)
    }

    override fun searchReadLaterArticles(search: ReadLaterArticleSearch, termsToSearchFor: List<String>) {
        readLaterArticleIndexWriterAndSearcher.searchReadLaterArticles(search, termsToSearchFor)
    }

}