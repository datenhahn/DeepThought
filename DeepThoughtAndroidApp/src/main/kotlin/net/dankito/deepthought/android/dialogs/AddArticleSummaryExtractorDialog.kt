package net.dankito.deepthought.android.dialogs

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.View.VISIBLE
import android.view.inputmethod.EditorInfo
import android.widget.ListView
import android.widget.TextView
import kotlinx.android.synthetic.main.dialog_add_article_summary_extractor.*
import kotlinx.android.synthetic.main.dialog_add_article_summary_extractor.view.*
import net.dankito.utils.AsyncResult
import net.dankito.deepthought.android.R
import net.dankito.deepthought.android.adapter.FoundFeedAddressesAdapter
import net.dankito.deepthought.android.di.AppComponent
import net.dankito.deepthought.model.ArticleSummaryExtractorConfig
import net.dankito.deepthought.news.article.ArticleExtractorManager
import net.dankito.deepthought.ui.IRouter
import net.dankito.feedaddressextractor.FeedAddress
import net.dankito.feedaddressextractor.FeedAddressExtractor
import net.dankito.feedaddressextractor.FeedType
import net.dankito.newsreader.feed.IFeedReader
import net.dankito.newsreader.model.FeedArticleSummary
import net.dankito.utils.ui.dialogs.IDialogService
import javax.inject.Inject


class AddArticleSummaryExtractorDialog : DialogFragment() {

    companion object {
        const val TAG = "ADD_ARTICLE_SUMMARY_EXTRACTOR_DIALOG"
    }


    @Inject
    protected lateinit var feedReader: IFeedReader

    @Inject
    protected lateinit var feedAddressExtractor:FeedAddressExtractor

    @Inject
    protected lateinit var articleExtractorManager: ArticleExtractorManager

    @Inject
    protected lateinit var router: IRouter

    @Inject
    protected lateinit var dialogService: IDialogService


    private val feedAddressesAdapter = FoundFeedAddressesAdapter()

    private var showingFeedAddressesForUrl: String? = null

    private var lstFeedSearchResults: ListView? = null
    private var txtFeedSearchResultsLabel: TextView? = null


    init {
        AppComponent.component.inject(this)
    }


    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater?.inflate(R.layout.dialog_add_article_summary_extractor, container)

        view?.let {
            view.btnCancel.setOnClickListener { dismiss() }

            view.btnCheckFeedOrWebsiteUrl?.setOnClickListener { checkFeedOrWebsiteUrl(view.edtxtFeedOrWebsiteUrl.text.toString()) }

            txtFeedSearchResultsLabel = view.txtFeedSearchResultsLabel

            this.lstFeedSearchResults = view.lstFeedSearchResults
            view.lstFeedSearchResults.adapter = feedAddressesAdapter
            view.lstFeedSearchResults.setOnItemClickListener { _, _, position, _ -> foundFeedAddressSelected(position) }

            view.edtxtFeedOrWebsiteUrl.addTextChangedListener(edtxtFeedOrWebsiteUrlTextWatcher)
            view.edtxtFeedOrWebsiteUrl.setOnEditorActionListener { _, actionId, keyEvent -> handleEditFeedOrWebsiteUrlAction(actionId, keyEvent) }
            view.edtxtFeedOrWebsiteUrl.setOnFocusChangeListener { _, hasFocus ->
                if(hasFocus) {
                    dialog.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                }
            }
        }

        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) // so that keyboard doesn't cover OK and Cancel buttons
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)

        return view
    }

    private fun foundFeedAddressSelected(position: Int) {
        val feedAddress = feedAddressesAdapter.getItem(position)

        feedReader.readFeedAsync(feedAddress.url) {
            retrievedFeedArticleSummary(feedAddress, it)
        }
    }

    private fun retrievedFeedArticleSummary(feedAddress: FeedAddress, result: AsyncResult<FeedArticleSummary>) {
        if(isDetached) { // got detached while result has been retrieved
            return
        }

        result.result?.let { addFeed(feedAddress.url, it) }
        result.error?.let { showError(feedAddress.url, it) }
    }

    private fun checkFeedOrWebsiteUrl(enteredFeedOrWebsiteUrl: String) {
        val feedOrWebsiteUrl = sanitizeUrl(enteredFeedOrWebsiteUrl)

        feedReader.readFeedAsync(feedOrWebsiteUrl) {
            if(it.result != null) {
                this.showingFeedAddressesForUrl = null // feed address entered, so we don't need entered text as siteUrl
                addFeed(feedOrWebsiteUrl, it.result as FeedArticleSummary)
            }
            // a lot of users entered an article url here. if so extract and show item // TODO: show hint how to correctly do it
            else if(couldBeArticleUrl(enteredFeedOrWebsiteUrl)) {
                tryToExtractArticle(feedOrWebsiteUrl)
            }
            else {
                tryToExtractFeedAdresses(feedOrWebsiteUrl)
            }
        }
    }

    private fun couldBeArticleUrl(enteredFeedOrWebsiteUrl: String): Boolean {
        try {
            val uri = Uri.parse(enteredFeedOrWebsiteUrl)
            val removedSchemeAndHost = uri.toString().replace(uri.scheme, "").replace("//", "").replace(uri.host, "").replace("www.", "")

            if(uri.path == null) {
                return false
            }
            else {
                return uri.query != null || uri.fragment != null || removedSchemeAndHost.length > 15 || uri.path.count { it == '/' } > 1
            }
        } catch(ignored: Exception) { }

        return false
    }

    private fun tryToExtractArticle(feedOrWebsiteUrl: String) {
        // here it makes no sense to just create an ExtractionResult with url in it, we have to figure out if an article can be extracted from that url
        articleExtractorManager.extractArticleUserDidSeeBeforeAndAddDefaultDataAsync(feedOrWebsiteUrl) {
            if(it.result?.couldExtractContent == true) {
                it.result?.let { router.showEditItemView(it) }
                dismiss()
            }
            else {
                tryToExtractFeedAdresses(feedOrWebsiteUrl)
            }
        }
    }

    private fun tryToExtractFeedAdresses(feedOrWebsiteUrl: String) {
        feedAddressExtractor.extractFeedAddressesAsync(feedOrWebsiteUrl) { asyncResult ->
            handleExtractFeedAddressesResult(feedOrWebsiteUrl, asyncResult)
        }
    }

    private fun sanitizeUrl(enteredFeedOrWebsiteUrl: String): String {
        var feedOrWebsiteUrl = enteredFeedOrWebsiteUrl.trim()

        if(feedOrWebsiteUrl.startsWith("http") == false) {
            if(feedOrWebsiteUrl.startsWith("www.") == false && feedOrWebsiteUrl.startsWith("/") == false) {
                feedOrWebsiteUrl = "www." + feedOrWebsiteUrl
            }

            val slashesToAdd = if(feedOrWebsiteUrl.startsWith("//")) ""
            else if(feedOrWebsiteUrl.startsWith("/")) "/"
            else "//"

            feedOrWebsiteUrl = "http:" + slashesToAdd + feedOrWebsiteUrl // TODO: what about https variant?
        }

        return feedOrWebsiteUrl
    }

    private fun handleExtractFeedAddressesResult(feedOrWebsiteUrl: String, asyncResult: AsyncResult<List<FeedAddress>>) {
        if(isDetached) { // got detached while result has been retrieved
            return
        }

        if(asyncResult.result != null) {
            val feedAddresses = asyncResult.result as List<FeedAddress>

            if(feedAddresses.isEmpty()) {
                showNoFeedAddressesFoundError(feedOrWebsiteUrl)
            }
            else {
                showFoundFeedAddresses(feedAddresses, feedOrWebsiteUrl)
            }
        }
        else {
            asyncResult.error?.let { showError(feedOrWebsiteUrl, it) }
        }
    }

    private fun addFeed(feedUrl: String, summary: FeedArticleSummary) {
        activity?.let { activity ->
            activity.runOnUiThread {
                addFeedOnUIThread(activity, feedUrl, summary)
            }
        }
    }

    private fun addFeedOnUIThread(activity: Activity, feedUrl: String, summary: FeedArticleSummary) {
        val config = ArticleSummaryExtractorConfig(feedUrl, summary.title ?: "", summary.imageUrl, siteUrl = showingFeedAddressesForUrl ?: summary.siteUrl)
        val extractorConfigDialog = ArticleSummaryExtractorConfigDialog()

        extractorConfigDialog.editConfiguration(activity, config) { didEditConfiguration ->
            if(didEditConfiguration) {
                feedAdded(feedUrl, summary, config)
            }
        }
    }

    private fun feedAdded(feedUrl: String, summary: FeedArticleSummary, config: ArticleSummaryExtractorConfig) {
        activity?.let { activity ->
            activity.runOnUiThread {
                showArticleSummaryActivity(config, summary)

                dismiss()
            }
        }
    }

    private fun showArticleSummaryActivity(config: ArticleSummaryExtractorConfig, summary: FeedArticleSummary) {
        router.showArticleSummaryView(config, summary)
    }

    private fun showFoundFeedAddresses(result: List<FeedAddress>, feedOrWebsiteUrl: String) {
        activity?.runOnUiThread {
            this.showingFeedAddressesForUrl = feedOrWebsiteUrl

            val sortedFeeds = result.sortedByDescending { it.type == FeedType.Atom } // show Atom feeds at top
            feedAddressesAdapter.setItems(sortedFeeds)

            txtFeedSearchResultsLabel?.visibility = VISIBLE
            lstFeedSearchResults?.visibility = VISIBLE
        }
    }

    private fun showNoFeedAddressesFoundError(feedOrWebsiteUrl: String) {
        activity?.let {
            showError(getString(R.string.error_no_rss_or_atom_feed_found_for_url, feedOrWebsiteUrl))
        }
    }

    private fun showError(feedOrWebsiteUrl: String, error: Exception) {
        activity?.let { // it happened that activity was not set -> getString() throws an exception
            showError(getString(R.string.error_cannot_read_feed_or_extract_feed_addresses_from_url, feedOrWebsiteUrl, error.localizedMessage))
        }
    }

    private fun showError(error: String) {
        dialogService.showErrorMessage(error)
    }


    private fun handleEditFeedOrWebsiteUrlAction(actionId: Int, keyEvent: KeyEvent?): Boolean {
        if(actionId == EditorInfo.IME_ACTION_SEARCH || (actionId == EditorInfo.IME_NULL && keyEvent?.action == KeyEvent.ACTION_DOWN)) {
            checkFeedOrWebsiteUrl(edtxtFeedOrWebsiteUrl.text.toString())

            return true
        }

        return false
    }

    private val edtxtFeedOrWebsiteUrlTextWatcher = object : TextWatcher {

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }

        override fun afterTextChanged(editable: Editable) {
            btnCheckFeedOrWebsiteUrl.isEnabled = editable.toString().isNullOrBlank() == false
        }

    }

}