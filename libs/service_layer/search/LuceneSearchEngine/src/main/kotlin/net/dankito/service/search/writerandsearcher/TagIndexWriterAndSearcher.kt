package net.dankito.service.search.writerandsearcher

import net.dankito.deepthought.model.CalculatedTag
import net.dankito.deepthought.model.Item
import net.dankito.deepthought.model.Tag
import net.dankito.service.data.TagService
import net.dankito.service.data.messages.TagChanged
import net.dankito.service.eventbus.EventBusPriorities
import net.dankito.service.eventbus.IEventBus
import net.dankito.service.search.FieldName
import net.dankito.service.search.SortOption
import net.dankito.service.search.SortOrder
import net.dankito.service.search.results.FilteredTagsLazyLoadingLuceneSearchResultsList
import net.dankito.service.search.specific.FilteredTagsSearch
import net.dankito.service.search.specific.FilteredTagsSearchResult
import net.dankito.service.search.specific.TagsSearch
import net.dankito.service.search.specific.TagsSearchResult
import net.dankito.service.search.util.LazyLoadingList
import net.dankito.utils.IThreadPool
import net.dankito.utils.OsHelper
import net.engio.mbassy.listener.Handler
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import org.slf4j.LoggerFactory


class TagIndexWriterAndSearcher(tagService: TagService, eventBus: IEventBus, osHelper: OsHelper, threadPool: IThreadPool, private val itemIndexWriterAndSearcher: ItemIndexWriterAndSearcher)
    : IndexWriterAndSearcher<Tag>(tagService, eventBus, osHelper, threadPool) {

    companion object {
        private const val TAGS_DEFAULT_COUNT_MAX_SEARCH_RESULTS = 100000

        private const val FILTERED_TAGS_DEFAULT_COUNT_MAX_SEARCH_RESULTS = 10000

        private val log = LoggerFactory.getLogger(TagIndexWriterAndSearcher::class.java)
    }


    override fun getDirectoryName(): String {
        return "tags"
    }

    override fun getIdFieldName(): String {
        return FieldName.TagId
    }


    override fun addEntityFieldsToDocument(entity: Tag, doc: Document) {
        // for an not analyzed String it's important to index it lower case as only then lower case search finds it
        doc.add(StringField(FieldName.TagName, entity.name.toLowerCase(), Field.Store.NO))
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
        for(tagNameToSearchFor in tagNamesToSearchFor) {
            try {
                val interrupted = executeSearchForSingleTagName(search, tagNameToSearchFor)
                if(interrupted) {
                    return
                }
            } catch (e: Exception) {
                log.error("Could not parse query for $tagNameToSearchFor (overall search term was ${search.searchTerm}", e)
                search.errorOccurred(e)
            }
        }

        getAllRelevantTagsSorted(search)

        search.fireSearchCompleted()
    }

    private fun executeSearchForSingleTagName(search: TagsSearch, tagNameToSearchFor: String): Boolean {
        if(tagNameToSearchFor.isBlank()) {
            val query = WildcardQuery(Term(FieldName.TagName, "*"))

            if(search.isInterrupted) {
                return true
            }

            search.addResult(TagsSearchResult(tagNameToSearchFor, executeSearchTagsQuery(query)))

            return false
        }
        else {
            return executeSearchForNonBlankSingleTagName(search, tagNameToSearchFor)
        }
    }

    private fun executeSearchForNonBlankSingleTagName(search: TagsSearch, tagNameToSearchFor: String): Boolean {
        val searchTerm = tagNameToSearchFor.toLowerCase()
        if(search.isInterrupted) {
            return true
        }

        val exactMatches = getExactMatches(searchTerm)

        val tagsContainingTermQuery = WildcardQuery(Term(FieldName.TagName, "*$searchTerm*"))
        if(search.isInterrupted) {
            return true
        }

        search.addResult(TagsSearchResult(tagNameToSearchFor, executeSearchTagsQuery(tagsContainingTermQuery), exactMatches))

        return false
    }

    private fun getExactMatches(searchTerm: String): List<Tag> {
        val exactMatchQuery = TermQuery(Term(FieldName.TagName, searchTerm))

        return executeQuery(exactMatchQuery, Tag::class.java)
    }

    private fun getAllRelevantTagsSorted(search: TagsSearch) {
        val sortRelevantTagsQuery = BooleanQuery()

        for(result in search.results.results) {
            if(search.isInterrupted) {
                return
            }

            val searchTerm = result.searchTerm.toLowerCase()
            if(result == search.results.lastResult || result.hasSingleMatch()) { // from last TagSearchResult use all matches, but from all others only exact or single matches
                sortRelevantTagsQuery.add(WildcardQuery(Term(FieldName.TagName, "*$searchTerm*")), BooleanClause.Occur.SHOULD)
            }
            else if(result.hasExactMatches()) {
                sortRelevantTagsQuery.add(TermQuery(Term(FieldName.TagName, searchTerm)), BooleanClause.Occur.SHOULD)
            }
        }

        if(search.isInterrupted) {
            return
        }

        search.setRelevantMatchesSorted(executeSearchTagsQuery(sortRelevantTagsQuery))
    }

    private fun executeSearchTagsQuery(query: Query): List<Tag> {
        return executeQuery(query, Tag::class.java, TAGS_DEFAULT_COUNT_MAX_SEARCH_RESULTS, SortOption(FieldName.TagName, SortOrder.Ascending, SortField.Type.STRING))
    }


    fun searchFilteredTags(search: FilteredTagsSearch, termsToSearchFor: List<String>) {
        var itemsHavingFilteredTags: List<Item> = listOf()
        var tagsOnItemsContainingFilteredTags: List<Tag> = listOf()
        val tagsToFilterForIds = search.tagsToFilterFor.map { it.id }
        val query = BooleanQuery()

        search.tagsToFilterFor.forEach { tag ->
            query.add(BooleanClause(TermQuery(Term(FieldName.ItemTagsIds, tag.id)), BooleanClause.Occur.MUST))
        }

        if(search.isInterrupted) {
            return
        }

        try {
            itemIndexWriterAndSearcher.executeQuery(query, FILTERED_TAGS_DEFAULT_COUNT_MAX_SEARCH_RESULTS, SortOption(FieldName.ItemCreated, SortOrder.Descending, SortField.Type.LONG))?.let { (searcher, hits) ->
                val items = FilteredTagsLazyLoadingLuceneSearchResultsList(entityService.entityManager, searcher, hits, osHelper, threadPool)
                itemsHavingFilteredTags = items

                val tagIdsOnItemsContainingFilteredTags: Collection<String> = items.tagIdsOnResultItems.filter { tagsToFilterForIds.contains(it) == false }

                val sortedTagIdsOnItemsContainingFilteredTags = searchInGivenTags(tagIdsOnItemsContainingFilteredTags, termsToSearchFor)

                tagsOnItemsContainingFilteredTags = LazyLoadingList<Tag>(entityService.entityManager, Tag::class.java, sortedTagIdsOnItemsContainingFilteredTags.toMutableList())
            }
        } catch (ex: Exception) {
            log.error("Could not execute Query " + query.toString(), ex)
        }

        if(search.isInterrupted) {
            return
        }

        search.results = FilteredTagsSearchResult(itemsHavingFilteredTags, tagsOnItemsContainingFilteredTags)
        search.fireSearchCompleted()
    }

    private fun searchInGivenTags(givenTagIds: Collection<String>, tagNamesToSearchFor: List<String>): List<String> {
        val searchTermQuery = BooleanQuery()
        if(tagNamesToSearchFor.isEmpty()) {
            searchTermQuery.add(WildcardQuery(Term(FieldName.TagName, "*")), BooleanClause.Occur.SHOULD)
        }
        else {
            for(tagName in tagNamesToSearchFor) {
                searchTermQuery.add(WildcardQuery(Term(FieldName.TagName, "*$tagName*")), BooleanClause.Occur.SHOULD)
            }
        }

        executeQuery(searchTermQuery, TAGS_DEFAULT_COUNT_MAX_SEARCH_RESULTS, SortOption(FieldName.TagName, SortOrder.Ascending, SortField.Type.STRING))?.let { (searcher, hits) ->
            val tagIdsWithSearchTermIds = getIdsFromHits(searcher, hits) // TODO: does this keep sort ordering?

            tagIdsWithSearchTermIds.retainAll(givenTagIds)

            return tagIdsWithSearchTermIds
        }

        return listOf()
    }


    override fun createEntityChangedListener(): Any {
        return object {

            @Handler(priority = EventBusPriorities.Indexer)
            fun entityChanged(tagChanged: TagChanged) {
                if(tagChanged.entity is CalculatedTag == false) { // do not index CalculatedTags
                    handleEntityChange(tagChanged)
                }
            }

        }
    }

}