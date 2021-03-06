package net.dankito.service.search.specific


import net.dankito.deepthought.model.ReadLaterArticle
import net.dankito.service.search.Search
import net.dankito.service.search.SearchWithCollectionResult


class ReadLaterArticleSearch(searchTerm: String = Search.EmptySearchTerm, completedListener: (List<ReadLaterArticle>) -> Unit)
    : SearchWithCollectionResult<ReadLaterArticle>(searchTerm, completedListener) {

}
