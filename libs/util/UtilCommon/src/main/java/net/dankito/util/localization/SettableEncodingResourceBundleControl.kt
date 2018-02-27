package net.dankito.util.localization

import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.*


/**
 * <p>
 *  By default .properties files only supports ISO-8859-1 (Latin-1) as encoding.
 *  To be able to load non Latin-1 characters, a custom ResourceBundle.Control has to be written which reads properties file in other encodings (e.g. UTF-8).
 * </p>
 *
 * <p>Copied from https://stackoverflow.com/questions/4659929/how-to-use-utf-8-in-resource-properties-with-resourcebundle</p>
 */
open class SettableEncodingResourceBundleControl(private val encoding: String) : ResourceBundle.Control() {

    @Throws(IllegalAccessException::class, InstantiationException::class, IOException::class)
    override fun newBundle(baseName: String, locale: Locale, format: String, loader: ClassLoader, reload: Boolean): ResourceBundle? {
        // The below is a copyFile of the default implementation.
        val bundleName = toBundleName(baseName, locale)
        val resourceName = toResourceName(bundleName, "properties")

        val stream = loadStream(loader, reload, resourceName)

        if(stream != null) {
            try {
                // Only this line is changed to make it to read properties files as UTF-8.
                return ThrowNoErrorOnMissingValuePropertyResourceBundle(InputStreamReader(stream, encoding))
            } finally {
                stream.close()
            }
        }

        return null
    }

    protected open fun loadStream(loader: ClassLoader, reload: Boolean, resourceName: String?): InputStream? {
        if(reload) {
            val url = loader.getResource(resourceName)

            if(url != null) {
                val connection = url.openConnection()

                if(connection != null) {
                    connection.useCaches = false
                    return connection.getInputStream()
                }
            }
        }
        else {
            return SettableEncodingResourceBundleControl::class.java.classLoader.getResourceAsStream(resourceName)
        }

        return null
    }

}