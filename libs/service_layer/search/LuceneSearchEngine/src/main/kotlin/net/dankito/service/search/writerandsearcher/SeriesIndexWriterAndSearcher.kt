package net.dankito.service.search.writerandsearcher

import net.dankito.deepthought.model.Series
import net.dankito.service.data.SeriesService
import net.dankito.service.data.messages.SeriesChanged
import net.dankito.service.eventbus.EventBusPriorities
import net.dankito.service.eventbus.IEventBus
import net.dankito.service.search.FieldName
import net.dankito.service.search.SortOption
import net.dankito.service.search.SortOrder
import net.dankito.service.search.specific.SeriesSearch
import net.dankito.utils.IThreadPool
import net.engio.mbassy.listener.Handler
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.index.Term
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.*


class SeriesIndexWriterAndSearcher(seriesService: SeriesService, eventBus: IEventBus, threadPool: IThreadPool) : IndexWriterAndSearcher<Series>(seriesService, eventBus, threadPool) {


    override fun getDirectoryName(): String {
        return "series"
    }

    override fun getIdFieldName(): String {
        return FieldName.SeriesId
    }


    override fun addEntityFieldsToDocument(entity: Series, doc: Document) {
        // for an not analyzed String it's important to index it lower case as only then lower case search finds it
        doc.add(StringField(FieldName.SeriesTitle, entity.title.toLowerCase(), Field.Store.NO))
    }


    fun searchSeries(search: SeriesSearch, termsToSearchFor: List<String>) {
        val query = BooleanQuery()

        addQueryForSearchTerm(termsToSearchFor, query)

        if(search.isInterrupted) {
            return
        }

        executeQueryForSearchWithCollectionResult(search, query, Series::class.java, sortOptions = SortOption(FieldName.SeriesTitle, SortOrder.Ascending, SortField.Type.STRING))
    }

    private fun addQueryForSearchTerm(termsToFilterFor: List<String>, query: BooleanQuery) {
        if(termsToFilterFor.isEmpty()) {
            query.add(WildcardQuery(Term(getIdFieldName(), "*")), BooleanClause.Occur.MUST)
        }
        else {
            for(term in termsToFilterFor) {
                val escapedTerm = QueryParser.escape(term)

                query.add(PrefixQuery(Term(FieldName.SeriesTitle, escapedTerm)), BooleanClause.Occur.MUST)
            }
        }
    }


    override fun createEntityChangedListener(): Any {
        return object {

            @Handler(priority = EventBusPriorities.Indexer)
            fun entityChanged(seriesChanged: SeriesChanged) {
                handleEntityChange(seriesChanged)
            }

        }
    }

}