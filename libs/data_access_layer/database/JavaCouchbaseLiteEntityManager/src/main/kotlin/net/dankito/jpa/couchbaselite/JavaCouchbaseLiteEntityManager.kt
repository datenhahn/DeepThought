package net.dankito.jpa.couchbaselite

import com.couchbase.lite.Context
import net.dankito.jpa.entitymanager.EntityManagerConfiguration
import net.dankito.util.settings.ILocalSettingsStore
import java.io.File


class JavaCouchbaseLiteEntityManager(configuration: EntityManagerConfiguration, localSettingsStore: ILocalSettingsStore)
    : DeepThoughtCouchbaseLiteEntityManagerBase(DeepThoughtJavaContext(configuration.dataFolder), localSettingsStore) {

    override fun adjustDatabasePath(context: Context, configuration: EntityManagerConfiguration): String {
        // TODO: implement this in a better way as this uses implementation internal details
        return File(context.filesDir, configuration.databaseName + ".cblite2").absolutePath
    }

}