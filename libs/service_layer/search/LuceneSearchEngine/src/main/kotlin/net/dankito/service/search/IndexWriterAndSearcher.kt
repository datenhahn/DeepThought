package net.dankito.service.search

import net.dankito.deepthought.model.BaseEntity
import net.dankito.service.data.EntityServiceBase
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.document.Document
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.store.LockObtainFailedException
import org.apache.lucene.util.Version
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*


abstract class IndexWriterAndSearcher<TEntity : BaseEntity>(val entityService: EntityServiceBase<TEntity>) {

    companion object {
        private const val WAIT_TIME_BEFORE_COMMITTING_INDICES_MILLIS = 1500L

        private val log = LoggerFactory.getLogger(IndexWriterAndSearcher::class.java)
    }


    var isReadOnly = false

    private var directory: Directory? = null

    private var writer: IndexWriter? = null

    private var directoryReader: DirectoryReader? = null

    private var indexSearcher: IndexSearcher? = null

    private var commitIndicesTimer: Timer? = null


    fun createDirectory(indexBaseDir: File) : Directory? {
        val indexDirectory = File(indexBaseDir, getDirectoryName())

        directory = FSDirectory.open(indexDirectory)

        return directory
    }

    abstract fun getDirectoryName(): String


    /**
     *
     *
     * Creates a new IndexWriter with specified Analyzer.
     * *
     * @return Created IndexWriter or null on failure!
     */
    @Throws(Exception::class)
    fun createIndexWriter(defaultAnalyzer: Analyzer): IndexWriter? {
        try {
            val config = IndexWriterConfig(Version.LUCENE_47, defaultAnalyzer)
            config.openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND

            this.writer = IndexWriter(directory, config)
        } catch(e: Exception) {
            if (e is LockObtainFailedException) {
                isReadOnly = true
            }

            log.error("Could not create IndexWriter for $this", e)
            throw e
        }

        return writer
    }

    /**
     *
     *
     * On opening an index directory there are no new changes yet
     * so on first call call this simple method to create an IndexSearcher.
     *
     * @return
     */
    fun createIndexSearcher(isReadOnly: Boolean) {
        directoryReader = createDirectoryReader(isReadOnly)

        indexSearcher = IndexSearcher(directoryReader)
    }

    private fun getIndexSearcher(): IndexSearcher? {
        if (indexSearcher == null) {
            try {
                createIndexSearcher(isReadOnly)
            } catch (ex: Exception) {
                log.error("Could not create IndexSearcher for $this", ex)
            }

        }

        return indexSearcher
    }

    private fun createDirectoryReader(isReadOnly: Boolean): DirectoryReader? {
        if (isReadOnly) {
            return DirectoryReader.open(directory) // open readonly
        }
        else if (directoryReader == null) { // on startup
            return DirectoryReader.open(writer, true)
        }
        else {
            val newDirectoryReader = DirectoryReader.openIfChanged(directoryReader, writer, true)

            if (newDirectoryReader != null) {
                return newDirectoryReader
            }
            else {
                return directoryReader
            }
        }
    }


    fun indexAllEntities() {
        for(entity in entityService.getEntries()) {
            indexEntity(entity)
        }
    }

    fun indexEntity(entity: TEntity) {
        try {
            val doc = createDocumentFromEntry(entity)

            indexDocument(doc)
        } catch (ex: Exception) {
            log.error("Could not index Entity " + entity, ex)
        }
    }

    abstract fun createDocumentFromEntry(entity: TEntity): Document


    fun deleteIndex() {
        writer?.let { writer ->
            writer.deleteAll()
            writer.prepareCommit()
            writer.commit()

            markIndexHasBeenUpdated()
        }
    }


    protected fun indexDocument(doc: Document) {
        try {
            writer?.let { writer ->
                log.info("Indexing document {}", doc)
                writer.addDocument(doc)

                startCommitIndicesTimer()

                markIndexHasBeenUpdated() // so that on next search updates are reflected
            }
        } catch (ex: Exception) {
            log.error("Could not index Document " + doc, ex)
        }
    }


    /**
     * Calling commit() is a costly operation
     * -> don't call it on each update / deletion, wait some time before commit accumulated changes.
     */
    @Synchronized protected fun startCommitIndicesTimer() {
        if (commitIndicesTimer != null) { // timer already started
            return
        }

        commitIndicesTimer = Timer("Commit Indices Timer")

        commitIndicesTimer?.schedule(object : TimerTask() {
            override fun run() {
                commitIndicesTimer = null

                writer?.commit()
            }
        }, WAIT_TIME_BEFORE_COMMITTING_INDICES_MILLIS)
    }


    private fun markIndexHasBeenUpdated() {
        indexSearcher = null
    }

}