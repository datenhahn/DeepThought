package net.dankito.deepthought.service.import_export

import net.dankito.deepthought.data.EntryPersister
import net.dankito.deepthought.data.ReferencePersister
import net.dankito.deepthought.data.SeriesPersister
import net.dankito.service.data.TagService
import net.dankito.service.search.ISearchEngine
import java.util.*


class DataImporterExporterManager(private val searchEngine: ISearchEngine, private val entryPersister: EntryPersister, private val tagService: TagService,
                                  private val referencePersister: ReferencePersister, private val seriesPersister: SeriesPersister) {

    val importer: List<IDataImporter> = ArrayList<IDataImporter>()
        get

    val exporter: Collection<IDataExporter> = ArrayList<IDataExporter>()
        get


    init {
        createImporterAndExporter()
    }


    private fun createImporterAndExporter() {

    }

}