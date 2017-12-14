package net.dankito.deepthought.di

import dagger.Component
import net.dankito.deepthought.data.EntryPersister
import net.dankito.deepthought.data.ReferencePersister
import net.dankito.deepthought.data.SeriesPersister
import net.dankito.deepthought.news.article.ArticleExtractorManager
import net.dankito.deepthought.news.summary.config.ArticleSummaryExtractorConfigManager
import net.dankito.deepthought.ui.presenter.*
import javax.inject.Singleton


@Singleton
@Component(modules = arrayOf(CommonModule::class, CommonDataModule::class, BaseModule::class))
interface CommonComponent : BaseComponent {

    companion object {
        lateinit var component: CommonComponent
    }


    fun inject(entriesListPresenter: EntriesListPresenter)

    fun inject(tagsListPresenter: TagsListPresenter)

    fun inject(referencesListPresenter: ReferencesListPresenter)

    fun inject(articleSummaryExtractorConfigManager: ArticleSummaryExtractorConfigManager)

    fun inject(articleSummaryPresenter: ArticleSummaryPresenter)

    fun inject(articleSummaryExtractorConfigPresenter: ArticleSummaryExtractorConfigPresenter)

    fun inject(readLaterArticleListPresenter: ReadLaterArticleListPresenter)

    fun inject(editEntryPresenter: EditEntryPresenter)

    fun inject(tagsOnEntryListPresenter: TagsOnEntryListPresenter)

    fun inject(seriesPresenterBase: SeriesPresenterBase)

    fun inject(articleExtractorManager: ArticleExtractorManager)

}