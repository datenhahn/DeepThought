package net.dankito.deepthought.android.di

import android.content.Context
import dagger.Module
import dagger.Provides
import net.dankito.deepthought.android.service.hashing.AndroidBase64Service
import net.dankito.deepthought.android.service.settings.AndroidPlatformConfiguration
import net.dankito.deepthought.service.importexport.pdf.PdfImporter
import net.dankito.filechooserdialog.service.PreviewImageService
import net.dankito.filechooserdialog.service.ThumbnailService
import net.dankito.jpa.couchbaselite.AndroidCouchbaseLiteEntityManager
import net.dankito.jpa.couchbaselite.CouchbaseLiteDatabaseUtil
import net.dankito.jpa.couchbaselite.CouchbaseLiteEntityManagerBase
import net.dankito.jpa.entitymanager.EntityManagerConfiguration
import net.dankito.jpa.entitymanager.IEntityManager
import net.dankito.mime.MimeTypeCategorizer
import net.dankito.mime.MimeTypeDetector
import net.dankito.synchronization.device.discovery.IDevicesDiscoverer
import net.dankito.synchronization.device.discovery.udp.AndroidUdpDevicesDiscoverer
import net.dankito.util.IThreadPool
import net.dankito.util.filesystem.AndroidFileStorageService
import net.dankito.util.filesystem.IFileStorageService
import net.dankito.util.hashing.IBase64Service
import net.dankito.util.network.INetworkConnectivityManager
import net.dankito.util.settings.ILocalSettingsStore
import net.dankito.utils.IPlatformConfiguration
import net.dankito.utils.database.IDatabaseUtil
import javax.inject.Singleton


@Module
open class AndroidCommonModule {

    @Provides
    @Singleton
    open fun providePlatformConfiguration(context: Context) : IPlatformConfiguration {
        return AndroidPlatformConfiguration(context)
    }


    @Provides
    @Singleton
    open fun provideEntityManager(context: Context, configuration: EntityManagerConfiguration, localSettingsStore: ILocalSettingsStore) : IEntityManager {
        return AndroidCouchbaseLiteEntityManager(context, localSettingsStore)
    }

    @Provides
    @Singleton
    fun provideDatabaseUtil(entityManager: IEntityManager) : IDatabaseUtil {
        return CouchbaseLiteDatabaseUtil(entityManager as CouchbaseLiteEntityManagerBase)
    }

    @Provides
    @Singleton
    open fun provideDevicesDiscoverer(context: Context, networkConnectivityManager: INetworkConnectivityManager, threadPool: IThreadPool) : IDevicesDiscoverer {
        return AndroidUdpDevicesDiscoverer(context, networkConnectivityManager, threadPool)
    }

    @Provides
    @Singleton
    open fun provideFileStorageService(context: Context) : IFileStorageService {
        return AndroidFileStorageService(context)
    }


    @Provides
    @Singleton
    open fun provideThumbnailService(context: Context, mimeTypeDetector: MimeTypeDetector, mimeTypeCategorizer: MimeTypeCategorizer) : ThumbnailService {
        return ThumbnailService(context, mimeTypeDetector, mimeTypeCategorizer)
    }

    @Provides
    @Singleton
    open fun providePreviewImageService(thumbnailService: ThumbnailService, mimeTypeDetector: MimeTypeDetector, mimeTypeCategorizer: MimeTypeCategorizer) : PreviewImageService {
        return PreviewImageService(thumbnailService, mimeTypeDetector, mimeTypeCategorizer)
    }


    @Provides
    @Singleton
    open fun providePdfImporter(applicationContext: Context, threadPool: IThreadPool) : PdfImporter {
        return PdfImporter(applicationContext, threadPool)
    }


    @Provides
    @Singleton
    open fun provideBase64Service() : IBase64Service {
        return AndroidBase64Service()
    }

}