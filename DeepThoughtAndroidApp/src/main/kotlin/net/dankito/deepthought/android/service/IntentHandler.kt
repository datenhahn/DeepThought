package net.dankito.deepthought.android.service

import android.content.Intent
import net.dankito.deepthought.model.Item
import net.dankito.deepthought.ui.IRouter
import net.dankito.utils.web.UrlUtil


class IntentHandler(private val extractArticleHandler: ExtractArticleHandler, private val router: IRouter, private val urlUtil: UrlUtil) {

    fun handle(intent: Intent) {
        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND == action && type != null) {
            handleActionSendIntent(type, intent)
        }
        else if (Intent.ACTION_SEND_MULTIPLE == action && type != null) {
            handleActionSendMultipleIntent()
        }
    }


    private fun handleActionSendIntent(type: String?, intent: Intent) {
        if("text/plain" == type) {
            handleReceivedPlainText(intent)
        }
        else if("text/html" == type) {
            handleReceivedText(intent)
        }
    }

    private fun handleReceivedPlainText(intent: Intent) {
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText ->
            val trimmedText = sharedText.trim() // K9 Mail sometimes add empty lines at the end
            if(urlUtil.isHttpUri(trimmedText)) {
                extractArticleHandler.extractAndShowArticleUserDidSeeBefore(trimmedText)
            }
            else {
                handleReceivedText(intent, sharedText)
            }
        }
    }

    private fun handleReceivedText(intent: Intent) {
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText ->
            handleReceivedText(intent, sharedText)
        }
    }

    private fun handleReceivedText(intent: Intent, sharedText: String) {
        var abstractPlain: String? = intent.getStringExtra(Intent.EXTRA_SUBJECT) // e.g. Firefox also sends Page Title // TODO: shouldn't it then be used as source title?
        if(abstractPlain == null && intent.hasExtra(Intent.EXTRA_TITLE)) {
            abstractPlain = intent.getStringExtra(Intent.EXTRA_TITLE)
        }

        router.showEditItemView(Item("<p>$sharedText</p>", abstractPlain ?: ""))
    }


    private fun handleActionSendMultipleIntent() {
        //            if (type.startsWith("image/")) {
        //                handleReceivedMultipleImages(intent)
        //            }
    }


}