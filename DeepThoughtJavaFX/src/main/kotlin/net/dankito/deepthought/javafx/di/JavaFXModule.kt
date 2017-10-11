package net.dankito.deepthought.javafx.di

import dagger.Module
import dagger.Provides
import net.dankito.data_access.network.communication.callback.IDeviceRegistrationHandler
import net.dankito.data_access.network.webclient.IWebClient
import net.dankito.deepthought.data.EntryPersister
import net.dankito.deepthought.data.ReferencePersister
import net.dankito.deepthought.data.SeriesPersister
import net.dankito.deepthought.javafx.appstart.CommunicationManagerStarter
import net.dankito.deepthought.javafx.appstart.JavaFXAppInitializer
import net.dankito.deepthought.javafx.dialogs.JavaFXDialogService
import net.dankito.deepthought.javafx.dialogs.mainwindow.MainWindowController
import net.dankito.deepthought.javafx.routing.JavaFXRouter
import net.dankito.deepthought.javafx.service.clipboard.JavaFXClipboardService
import net.dankito.deepthought.javafx.service.communication.JavaFXDeviceRegistrationHandler
import net.dankito.deepthought.javafx.service.network.JavaFXNetworkConnectivityManager
import net.dankito.deepthought.javafx.service.settings.JavaFXLocalSettingsStore
import net.dankito.deepthought.news.article.ArticleExtractorManager
import net.dankito.deepthought.service.data.DataManager
import net.dankito.deepthought.service.import_export.DataImporterExporterManager
import net.dankito.deepthought.ui.IRouter
import net.dankito.deepthought.ui.presenter.ArticleSummaryPresenter
import net.dankito.newsreader.summary.IImplementedArticleSummaryExtractorsManager
import net.dankito.service.data.ReadLaterArticleService
import net.dankito.service.data.TagService
import net.dankito.service.search.ISearchEngine
import net.dankito.service.synchronization.initialsync.InitialSyncManager
import net.dankito.utils.IThreadPool
import net.dankito.utils.localization.Localization
import net.dankito.utils.services.network.INetworkConnectivityManager
import net.dankito.utils.services.network.NetworkHelper
import net.dankito.utils.settings.ILocalSettingsStore
import net.dankito.utils.ui.IClipboardService
import net.dankito.utils.ui.IDialogService
import javax.inject.Singleton


@Module
class JavaFXModule(private val flavorInstanceProvider: JavaFXInstanceProvider, private val mainWindowController: MainWindowController) {

    @Provides
    @Singleton
    fun provideAppInitializer() : JavaFXAppInitializer {
        return JavaFXAppInitializer()
    }

    @Provides
    @Singleton
    fun provideLocalSettingsStore() : ILocalSettingsStore {
        return JavaFXLocalSettingsStore()
    }

    @Provides
    @Singleton
    fun provideCommunicationManagerStarter(dataManager: DataManager) : CommunicationManagerStarter {
        return CommunicationManagerStarter(dataManager)
    }


    @Provides
    @Singleton
    fun provideRouter() : IRouter {
        return JavaFXRouter(mainWindowController)
    }


    @Provides
    @Singleton
    fun provideClipboardService() : IClipboardService {
        return JavaFXClipboardService()
    }

    @Provides
    @Singleton
    fun provideDialogService(localization: Localization) : IDialogService {
        return JavaFXDialogService(localization)
    }


    @Provides
    @Singleton
    fun provideDeviceRegistrationHandler(dataManager: DataManager, initialSyncManager: InitialSyncManager, dialogService: IDialogService, localization: Localization) : IDeviceRegistrationHandler {
        return JavaFXDeviceRegistrationHandler(dataManager, initialSyncManager, dialogService, localization)
    }

    @Provides
    @Singleton
    fun provideNetworkConnectivityManager(networkHelper: NetworkHelper) : INetworkConnectivityManager {
        return JavaFXNetworkConnectivityManager(networkHelper)
    }


    @Provides
    @Singleton
    fun provideArticleSummaryPresenter(entryPersister: EntryPersister, readLaterArticleService: ReadLaterArticleService, articleExtractorManager: ArticleExtractorManager,
                                       router: IRouter, clipboardService: IClipboardService, dialogService: IDialogService) : ArticleSummaryPresenter {
        return flavorInstanceProvider.provideArticleSummaryPresenter(entryPersister, readLaterArticleService, articleExtractorManager, router, clipboardService, dialogService)
    }

    @Provides
    @Singleton
    fun provideImplementedArticleSummaryExtractorsManager(webClient: IWebClient) : IImplementedArticleSummaryExtractorsManager {
        return flavorInstanceProvider.provideImplementedArticleSummaryExtractorsManager(webClient)
    }


    // TODO: move to CommonModule again as soon as importing/exporting is implemented in Android
    @Provides
    @Singleton
    fun provideDataImporterExporterManager(searchEngine: ISearchEngine, entryPersister: EntryPersister, tagService: TagService,
                                           referencePersister: ReferencePersister, seriesPersister: SeriesPersister, threadPool: IThreadPool)
            : DataImporterExporterManager {
        return DataImporterExporterManager(searchEngine, entryPersister, tagService, referencePersister, seriesPersister, threadPool)
    }

}