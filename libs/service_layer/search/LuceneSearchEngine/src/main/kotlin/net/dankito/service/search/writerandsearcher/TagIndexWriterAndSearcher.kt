package net.dankito.service.search.writerandsearcher

import net.dankito.deepthought.model.Tag
import net.dankito.service.data.TagService
import net.dankito.service.data.messages.TagChanged
import net.dankito.service.search.FieldName
import net.dankito.service.search.SortOption
import net.dankito.service.search.SortOrder
import net.dankito.service.search.specific.TagsSearch
import net.dankito.service.search.specific.TagsSearchResult
import net.engio.mbassy.listener.Handler
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.index.Term
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.*
import org.slf4j.LoggerFactory


class TagIndexWriterAndSearcher(tagService: TagService) : IndexWriterAndSearcher<Tag>(tagService) {

    companion object {
        private const val TAGS_DEFAULT_COUNT_MAX_SEARCH_RESULTS = 100000

        private val log = LoggerFactory.getLogger(TagIndexWriterAndSearcher::class.java)
    }


    override fun getDirectoryName(): String {
        return "tags"
    }

    override fun getIdFieldName(): String {
        return FieldName.TagId
    }


    override fun createDocumentForEntity(entity: Tag): Document {
        val doc = Document()

        doc.add(StringField(getIdFieldName(), entity.id, Field.Store.YES))
        // for an not analyzed String it's important to index it lower case as only then lower case search finds it
        doc.add(StringField(FieldName.TagName, entity.name.toLowerCase(), Field.Store.NO))

        return doc
    }


    fun searchTags(search: TagsSearch, termsToSearchFor: List<String>) {
        if(termsToSearchFor.isEmpty()) {
            executeSearchForEmptySearchTerm(search)
        }
        else {
            executeSearchForNonEmptySearchTerm(search, termsToSearchFor)
        }
    }

    private fun executeSearchForEmptySearchTerm(search: TagsSearch) {
        val query = WildcardQuery(Term(FieldName.TagName, "*"))

        if(search.isInterrupted) {
            return
        }

        search.setRelevantMatchesSorted(executeSearchTagsQuery(query))

        search.fireSearchCompleted()
    }

    private fun executeSearchForNonEmptySearchTerm(search: TagsSearch, tagNamesToSearchFor: List<String>) {
        for (tagNameToFilterFor in tagNamesToSearchFor) {
            if (search.isInterrupted) {
                return
            }

            try {
                val searchTerm = QueryParser.escape(tagNameToFilterFor)
                if (search.isInterrupted) {
                    return
                }

                val exactMatch = getExactMatchTag(searchTerm)

                val query = WildcardQuery(Term(FieldName.TagName, "*$searchTerm*"))
                if (search.isInterrupted) {
                    return
                }

                search.addResult(TagsSearchResult(tagNameToFilterFor, executeSearchTagsQuery(query), exactMatch))
            } catch (e: Exception) {
                log.error("Could not parse query for $tagNameToFilterFor (overall search term was ${search.searchTerm}", e)
                search.errorOccurred(e)
            }

        }

        getAllRelevantTagsSorted(search)

        search.fireSearchCompleted()
    }

    private fun getExactMatchTag(searchTerm: String): Tag? {
        val exactMatchQuery = TermQuery(Term(FieldName.TagName, searchTerm))

        val exactMatchResults = executeQuery(exactMatchQuery, Tag::class.java, 2)

        return if(exactMatchResults.size == 1) exactMatchResults.get(0) else null
    }

    private fun getAllRelevantTagsSorted(search: TagsSearch) {
        val sortRelevantTagsQuery = BooleanQuery()

        for(result in search.results.results) {
            if(search.isInterrupted) {
                return
            }

            val searchTerm = QueryParser.escape(result.searchTerm)
            if (result.hasExactMatch() && result !== search.results.lastResult) {
                sortRelevantTagsQuery.add(TermQuery(Term(FieldName.TagName, searchTerm)), BooleanClause.Occur.SHOULD)
            }
            else {
                sortRelevantTagsQuery.add(WildcardQuery(Term(FieldName.TagName, "*$searchTerm*")), BooleanClause.Occur.SHOULD)
            }
        }

        if(search.isInterrupted) {
            return
        }

        val relevantMatchesSorted = executeSearchTagsQuery(sortRelevantTagsQuery)
        search.setRelevantMatchesSorted(relevantMatchesSorted)
    }

    private fun executeSearchTagsQuery(query: Query): List<Tag> {
        return executeQuery(query, Tag::class.java, TAGS_DEFAULT_COUNT_MAX_SEARCH_RESULTS, SortOption(FieldName.TagName, SortOrder.Ascending, SortField.Type.STRING))
    }


    override fun createEntityChangedListener(): Any {
        return object {

            @Handler(priority = Int.MAX_VALUE)
            fun entityChanged(tagChanged: TagChanged) {
                handleEntityChange(tagChanged)
            }

        }
    }

}