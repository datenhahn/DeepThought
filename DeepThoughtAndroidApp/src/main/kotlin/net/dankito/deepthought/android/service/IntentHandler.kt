package net.dankito.deepthought.android.service

import android.content.Intent
import net.dankito.deepthought.model.Item
import net.dankito.deepthought.news.article.ArticleExtractorManager
import net.dankito.deepthought.ui.IRouter
import net.dankito.utils.UrlUtil
import net.dankito.utils.ui.IDialogService


class IntentHandler(private val articleExtractorManager: ArticleExtractorManager, private val router: IRouter, private val urlUtil: UrlUtil, private val dialogService: IDialogService) {

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
                articleExtractorManager.extractArticleAndAddDefaultDataAsync(trimmedText) {
                    it.result?.let { router.showEditEntryView(it) }
                    it.error?.let { showCouldNotExtractItemErrorMessage(it, sharedText) }
                }
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
        var abstractPlain: String? = intent.getStringExtra(Intent.EXTRA_SUBJECT) // e.g. Firefox also sends Page Title
        if(abstractPlain == null && intent.hasExtra(Intent.EXTRA_TITLE)) {
            abstractPlain = intent.getStringExtra(Intent.EXTRA_TITLE)
        }

        router.showEditEntryView(Item("<p>$sharedText</p>", "<p>$abstractPlain</p>"))
    }


    private fun handleActionSendMultipleIntent() {
        //            if (type.startsWith("image/")) {
        //                handleReceivedMultipleImages(intent)
        //            }
    }


    private fun showCouldNotExtractItemErrorMessage(error: Exception, articleUrl: String) {
        dialogService.showErrorMessage(dialogService.getLocalization().getLocalizedString("alert.message.could.not.extract.item.from.url", articleUrl), exception = error)
    }

}