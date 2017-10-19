package net.dankito.deepthought.android.activities

import android.os.Build
import android.os.Bundle
import android.support.v7.widget.ActionMenuView
import android.support.v7.widget.AppCompatImageView
import android.support.v7.widget.PopupMenu
import android.text.Html
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.github.clans.fab.FloatingActionMenu
import kotlinx.android.synthetic.main.activity_edit_entry.*
import kotlinx.android.synthetic.main.view_floating_action_button_entry_fields.*
import net.dankito.deepthought.android.R
import net.dankito.deepthought.android.activities.arguments.EditEntryActivityParameters
import net.dankito.deepthought.android.activities.arguments.EditEntryActivityResult
import net.dankito.deepthought.android.activities.arguments.EditReferenceActivityResult
import net.dankito.deepthought.android.di.AppComponent
import net.dankito.deepthought.android.dialogs.EditHtmlTextDialog
import net.dankito.deepthought.android.dialogs.TagsOnEntryDialogFragment
import net.dankito.deepthought.android.service.OnSwipeTouchListener
import net.dankito.deepthought.android.views.ContextHelpUtil
import net.dankito.deepthought.android.views.EditEntryActivityFloatingActionMenuButton
import net.dankito.deepthought.android.views.FullscreenWebView
import net.dankito.deepthought.android.views.ToolbarUtil
import net.dankito.deepthought.data.EntryPersister
import net.dankito.deepthought.model.*
import net.dankito.deepthought.model.extensions.entryPreview
import net.dankito.deepthought.model.extensions.getPlainTextForHtml
import net.dankito.deepthought.model.extensions.getPreviewWithSeriesAndPublishingDate
import net.dankito.deepthought.model.fields.ItemField
import net.dankito.deepthought.model.util.ItemExtractionResult
import net.dankito.deepthought.news.article.ArticleExtractorManager
import net.dankito.deepthought.service.data.DataManager
import net.dankito.deepthought.ui.IRouter
import net.dankito.deepthought.ui.presenter.EditEntryPresenter
import net.dankito.service.data.*
import net.dankito.service.data.messages.EntityChangeSource
import net.dankito.service.data.messages.EntryChanged
import net.dankito.service.eventbus.IEventBus
import net.dankito.utils.IThreadPool
import net.dankito.utils.ui.IClipboardService
import net.dankito.utils.ui.IDialogService
import net.engio.mbassy.listener.Handler
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList
import kotlin.concurrent.schedule


class EditEntryActivity : BaseActivity() {

    companion object {
        private const val ENTRY_ID_INTENT_EXTRA_NAME = "ENTRY_ID"
        private const val READ_LATER_ARTICLE_ID_INTENT_EXTRA_NAME = "READ_LATER_ARTICLE_ID"
        private const val ENTRY_EXTRACTION_RESULT_INTENT_EXTRA_NAME = "ENTRY_EXTRACTION_RESULT"

        private const val FORCE_SHOW_TAGS_PREVIEW_INTENT_EXTRA_NAME = "FORCE_SHOW_TAGS_PREVIEW"
        private const val FORCE_SHOW_REFERENCE_PREVIEW_INTENT_EXTRA_NAME = "FORCE_SHOW_REFERENCE_PREVIEW"
        private const val FORCE_SHOW_ABSTRACT_PREVIEW_INTENT_EXTRA_NAME = "FORCE_SHOW_ABSTRACT_PREVIEW"

        private const val IS_IN_READER_MODE_INTENT_EXTRA_NAME = "IS_IN_READER_MODE"

        private const val CONTENT_INTENT_EXTRA_NAME = "CONTENT"
        private const val ABSTRACT_INTENT_EXTRA_NAME = "ABSTRACT"
        private const val REFERENCE_INTENT_EXTRA_NAME = "REFERENCE"
        private const val TAGS_ON_ENTRY_INTENT_EXTRA_NAME = "TAGS_ON_ENTRY"

        const val ResultId = "EDIT_ENTRY_ACTIVITY_RESULT"

        private const val GetHtmlCodeFromWebViewJavaScriptInterfaceName = "HtmlViewer"
    }


    @Inject
    protected lateinit var entryService: EntryService

    @Inject
    protected lateinit var readLaterArticleService: ReadLaterArticleService

    @Inject
    protected lateinit var tagService: TagService

    @Inject
    protected lateinit var referenceService: ReferenceService

    @Inject
    protected lateinit var entryPersister: EntryPersister

    @Inject
    protected lateinit var deleteEntityService: DeleteEntityService

    @Inject
    protected lateinit var clipboardService: IClipboardService

    @Inject
    protected lateinit var router: IRouter

    @Inject
    protected lateinit var threadPool: IThreadPool

    @Inject
    protected lateinit var articleExtractorManager: ArticleExtractorManager

    @Inject
    protected lateinit var dialogService: IDialogService

    @Inject
    protected lateinit var eventBus: IEventBus


    private var item: Item? = null

    private var readLaterArticle: ReadLaterArticle? = null

    private var itemExtractionResult: ItemExtractionResult? = null


    private var originalInformation: String? = null

    private var originalTags: MutableCollection<Tag>? = null

    private var originalSource: Source? = null

    private var originalTitleAbstract: String? = null


    private var contentToEdit: String? = null

    private var abstractToEdit: String? = null

    private var sourceToEdit: Source? = null

    private val tagsOnEntry: MutableList<Tag> = ArrayList()

    private val changedFields = HashSet<ItemField>()

    private var forceShowTagsPreview = false

    private var forceShowReferencePreview = false

    private var forceShowAbstractPreview = false


    private val presenter: EditEntryPresenter

    private var isInReaderMode = false

    private var webSiteHtml: String? = null

    private var isLoadingUrl = false


    private val contextHelpUtil = ContextHelpUtil()

    private val toolbarUtil = ToolbarUtil()

    private var mnSaveEntry: MenuItem? = null

    private var mnDeleteExistingEntry: MenuItem? = null

    private var mnToggleReaderMode: MenuItem? = null

    private var mnSaveEntryExtractionResultForLaterReading: MenuItem? = null

    private var mnDeleteReadLaterArticle: MenuItem? = null

    private var mnShareEntry: MenuItem? = null

    private lateinit var floatingActionMenu: EditEntryActivityFloatingActionMenuButton


    private val dataManager: DataManager

    private var eventBusListener: EventBusListener? = null


    init {
        AppComponent.component.inject(this)

        dataManager = entryService.dataManager

        presenter = EditEntryPresenter(entryPersister, readLaterArticleService, clipboardService, router)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        parameterHolder.setActivityResult(ResultId, EditEntryActivityResult())

        setupUI()

        savedInstanceState?.let { restoreState(it) }

        if(savedInstanceState == null) {
            showParameters(getParameters() as? EditEntryActivityParameters)
        }
    }

    private fun restoreState(savedInstanceState: Bundle) {
        this.forceShowTagsPreview = savedInstanceState.getBoolean(FORCE_SHOW_TAGS_PREVIEW_INTENT_EXTRA_NAME, false)
        this.forceShowReferencePreview = savedInstanceState.getBoolean(FORCE_SHOW_REFERENCE_PREVIEW_INTENT_EXTRA_NAME, false)
        this.forceShowAbstractPreview = savedInstanceState.getBoolean(FORCE_SHOW_ABSTRACT_PREVIEW_INTENT_EXTRA_NAME, false)

        this.isInReaderMode = savedInstanceState.getBoolean(IS_IN_READER_MODE_INTENT_EXTRA_NAME, false)

        savedInstanceState.getString(ENTRY_EXTRACTION_RESULT_INTENT_EXTRA_NAME)?.let { editEntryExtractionResult(it) }
        savedInstanceState.getString(READ_LATER_ARTICLE_ID_INTENT_EXTRA_NAME)?.let { readLaterArticleId -> editReadLaterArticle(readLaterArticleId) }
        savedInstanceState.getString(ENTRY_ID_INTENT_EXTRA_NAME)?.let { entryId -> editEntry(entryId) }

        if(savedInstanceState.getString(ENTRY_EXTRACTION_RESULT_INTENT_EXTRA_NAME) == null && savedInstanceState.getString(READ_LATER_ARTICLE_ID_INTENT_EXTRA_NAME) == null &&
                savedInstanceState.getString(ENTRY_ID_INTENT_EXTRA_NAME) == null) { // a new Item is being created then
            createEntry(false) // don't go to EditHtmlTextDialog for content here as we're restoring state, content may already be set
        }

        getAndClearStringState(CONTENT_INTENT_EXTRA_NAME)?.let { content ->
            contentToEdit = content
            setContentPreviewOnUIThread()
        }

        savedInstanceState.getString(ABSTRACT_INTENT_EXTRA_NAME)?.let { abstract ->
            abstractToEdit = abstract
            setAbstractPreviewOnUIThread()
        }

        savedInstanceState.getString(REFERENCE_INTENT_EXTRA_NAME)?.let { referenceID -> restoreReference(referenceID) }

        savedInstanceState.getString(TAGS_ON_ENTRY_INTENT_EXTRA_NAME)?.let { tagsOnEntryIds -> restoreTagsOnEntryAsync(tagsOnEntryIds) }

        floatingActionMenu.restoreInstanceState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)

        outState?.let {
            outState.putString(ENTRY_ID_INTENT_EXTRA_NAME, null)
            item?.id?.let { entryId -> outState.putString(ENTRY_ID_INTENT_EXTRA_NAME, entryId) }

            outState.putString(READ_LATER_ARTICLE_ID_INTENT_EXTRA_NAME, null)
            readLaterArticle?.id?.let { readLaterArticleId -> outState.putString(READ_LATER_ARTICLE_ID_INTENT_EXTRA_NAME, readLaterArticleId) }

            outState.putString(ENTRY_EXTRACTION_RESULT_INTENT_EXTRA_NAME, null)
            itemExtractionResult?.let { outState.putString(ENTRY_EXTRACTION_RESULT_INTENT_EXTRA_NAME, serializeToTempFileOnDisk(it)) }

            outState.putBoolean(FORCE_SHOW_TAGS_PREVIEW_INTENT_EXTRA_NAME, forceShowTagsPreview)
            outState.putBoolean(FORCE_SHOW_REFERENCE_PREVIEW_INTENT_EXTRA_NAME, forceShowReferencePreview)
            outState.putBoolean(FORCE_SHOW_ABSTRACT_PREVIEW_INTENT_EXTRA_NAME, forceShowAbstractPreview)

            outState.putBoolean(IS_IN_READER_MODE_INTENT_EXTRA_NAME, isInReaderMode)

            outState.putString(TAGS_ON_ENTRY_INTENT_EXTRA_NAME, serializer.serializeObject(tagsOnEntry))

            outState.putString(REFERENCE_INTENT_EXTRA_NAME, sourceToEdit?.id)

            outState.putString(ABSTRACT_INTENT_EXTRA_NAME, abstractToEdit)

            floatingActionMenu.saveInstanceState(outState)
        }

        storeState(CONTENT_INTENT_EXTRA_NAME, contentToEdit) // application crashes if objects put into bundle are too large (> 1 MB) for Android
    }


    private fun setupUI() {
        setContentView(R.layout.activity_edit_entry)

        setSupportActionBar(toolbar)
        toolbarUtil.adjustToolbarLayoutDelayed(toolbar)

        supportActionBar?.let { actionBar ->
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setDisplayShowHomeEnabled(true)
        }

        lytAbstractPreview.setFieldNameOnUiThread(R.string.activity_edit_entry_abstract_label) { didAbstractChange -> abstractChanged(didAbstractChange) }
        lytAbstractPreview.fieldValueFocusChangedListener = { hasFocus ->
            if(hasFocus == false) {
                appliedChangesToAbstract(lytAbstractPreview.didValueChange)
            }
        }
        lytReferencePreview.setFieldNameOnUiThread(getString(R.string.activity_edit_entry_reference_label))
        lytTagsPreview.setFieldNameOnUiThread(getString(R.string.activity_edit_entry_tags_label))

        lytReferencePreview.setOnClickListener { editReference() }
        btnClearEntryReference.setOnClickListener { referenceCleared() }
        lytTagsPreview.setOnClickListener { editTagsOnEntry() }

        wbvwContent?.requestFocus() // avoid that lytAbstractPreview gets focus and keyboard therefore gets show on activity start

        floatingActionMenu = EditEntryActivityFloatingActionMenuButton(findViewById(R.id.floatingActionMenu) as FloatingActionMenu, { addTagsToEntry() },
                { addReferenceToEntry() }, { addAbstractToEntry() } )

        setupEntryContentView()
    }

    private fun addTagsToEntry() {
        editTagsOnEntry()

        forceShowTagsPreview = true
        setTagsOnEntryPreviewOnUIThread()
    }

    private fun addReferenceToEntry() {
        editReference()

        forceShowReferencePreview = true
        setReferencePreviewOnUIThread()
    }

    private fun addAbstractToEntry() {
        forceShowAbstractPreview = true
        setAbstractPreviewOnUIThread()

        lytAbstractPreview.startEditing()
    }

    private fun referenceCleared() {
        referenceChanged(null)
    }

    private fun setupEntryContentView() {
        txtEntryContentLabel.setOnClickListener { editContent() }

        wbvwContent.setOptionsBar(lytFullscreenWebViewOptionsBar)
        wbvwContent.changeFullscreenModeListener = { mode -> handleChangeFullscreenModeEvent(mode) }

        wbvwContent.singleTapListener = { handleWebViewSingleTap(it) }
        wbvwContent.swipeListener = { isInFullscreen, swipeDirection -> handleWebViewSwipe(isInFullscreen, swipeDirection) }

        val settings = wbvwContent.getSettings()
        settings.defaultTextEncodingName = "UTF-8" // otherwise non ASCII text doesn't get displayed correctly
        settings.defaultFontSize = 18 // default font is too small
        settings.domStorageEnabled = true // otherwise images may not load, see https://stackoverflow.com/questions/29888395/images-not-loading-in-android-webview
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.javaScriptEnabled = true // so that embedded videos etc. work

        wbvwContent.addJavascriptInterface(GetHtmlCodeFromWebViewJavaScripInterface { url, html -> siteFinishedLoading(url, html) }, GetHtmlCodeFromWebViewJavaScriptInterfaceName)

        wbvwContent.setWebChromeClient(object : WebChromeClient() {
            private var hasCompletelyFinishedLoadingPage = false
            private val timerCheckIfHasCompletelyFinishedLoadingPage = Timer()

            override fun onProgressChanged(webView: WebView, newProgress: Int) {
                super.onProgressChanged(webView, newProgress)

                if(newProgress < 100) {
                    hasCompletelyFinishedLoadingPage = false
                }
                else {
                    hasCompletelyFinishedLoadingPage = true
                    timerCheckIfHasCompletelyFinishedLoadingPage.schedule(1000L) { // 100 % may only means a part of the page but not the whole page is loaded -> wait some time and check if not loading another part of the page
                        if(hasCompletelyFinishedLoadingPage) {
                            webPageCompletelyLoaded(webView)
                        }
                    }
                }
            }
        })
    }

    private fun webPageCompletelyLoaded(webView: WebView) {
        runOnUiThread { webPageCompletelyLoadedOnUiThread(webView) }
    }

    private fun webPageCompletelyLoadedOnUiThread(webView: WebView) {
        // if ItemExtractionResult's item content hasn't been extracted yet, wait till WebView is loaded and extract item content then
        if((itemExtractionResult != null || readLaterArticle != null) && isInReaderMode == false &&
                webView.url != "about:blank" && webView.url.startsWith("data:text/html") == false) {
            webView.loadUrl("javascript:$GetHtmlCodeFromWebViewJavaScriptInterfaceName.finishedLoadingSite" +
                    "(document.URL, '<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');")
        }
        else if(item != null) {
            urlLoadedNow()
        }
    }

    private fun siteFinishedLoading(url: String, html: String) {
        urlLoadedNow()

        // now try to extract item content from WebView's html
        val extractionResult = itemExtractionResult ?: readLaterArticle?.itemExtractionResult
        if(extractionResult != null && isInReaderMode == false) {
            webSiteHtml = html
            contentToEdit = html

            if(extractionResult?.couldExtractContent == false) {
                extractionResult?.let { extractionResult ->
                    articleExtractorManager.extractArticleAndAddDefaultData(extractionResult, html, url)

                    if(extractionResult.couldExtractContent) {
                        runOnUiThread { extractedContentOnUiThread(extractionResult) }
                    }
                }
            }
        }
    }

    private fun urlLoadedNow() {
        isLoadingUrl = false
        wbvwContent.elementClickedListener = null

        runOnUiThread {
            wbvwContent.setWebViewClient(null) // now reactivate default url handling
            prgIsLoadingWebPage.visibility = View.GONE
        }
    }

    private fun extractedContentOnUiThread(extractionResult: ItemExtractionResult) { // extractionResult can either be from itemExtractionResult or readLaterArticle
        wbvwContent.removeJavascriptInterface(GetHtmlCodeFromWebViewJavaScriptInterfaceName)

        mnToggleReaderMode?.isVisible = extractionResult.couldExtractContent
        invalidateOptionsMenu()

        itemExtractionResult?.let {
            editEntryExtractionResult(it, false) // updates source and abstract, but avoids that extracted content gets shown (this is important according to our
            // lawyer, user must click on toggleReaderMode menu first)
        }

        readLaterArticle?.let { editReadLaterArticle(it, false) }
    }


    inner class GetHtmlCodeFromWebViewJavaScripInterface(private val retrievedHtmlCode: ((url: String, html: String) -> Unit)) {

        @JavascriptInterface
        @SuppressWarnings("unused")
        fun finishedLoadingSite(url: String, html: String) {
            retrievedHtmlCode(url, html)
        }

    }


    override fun onResume() {
        super.onResume()

        (getAndClearResult(EditReferenceActivity.ResultId) as? EditReferenceActivityResult)?.let { result ->
            appliedChangesToReference(result)
        }

        (supportFragmentManager.findFragmentByTag(TagsOnEntryDialogFragment.TAG) as? TagsOnEntryDialogFragment)?.let {
            it.restoreDialog(originalTags ?: ArrayList<Tag>()) { appliedChangesToTags(it) }
        }

        (supportFragmentManager.findFragmentByTag(EditHtmlTextDialog.TAG) as? EditHtmlTextDialog)?.let { dialog ->
            (dialog.view?.findViewById(R.id.toolbar) as? android.support.v7.widget.Toolbar)?.title?.let { toolbarTitle ->
                if(toolbarTitle == getString(R.string.activity_edit_entry_edit_content_title)) {
                    dialog.restoreDialog(contentToEdit ?: "") { appliedChangesToContent(it) }
                }
            }
        }

        setContentPreviewOnUIThread()

        mayRegisterEventBusListener()
    }

    private fun mayShowSaveEntryChangesHelpOnUIThread() {
        val localSettings = entryService.dataManager.localSettings

        if(localSettings.didShowSaveItemChangesHelp == false) {
            contextHelpUtil.showContextHelp(lytContextHelpSave, R.string.context_help_save_entry_changes)

            localSettings.didShowSaveItemChangesHelp = true
            entryService.dataManager.localSettingsUpdated()
        }
    }

    private fun savedReference(source: Source) {
        referenceChanged(source)

        itemExtractionResult?.series = null // TODO: why did i do this?
        readLaterArticle?.itemExtractionResult?.series = null
    }

    private fun referenceChanged(source: Source?) {
        sourceToEdit = source // do not set source directly on item as if item is not saved yet adding it to source.items causes an error

        updateEntryFieldChangedOnUIThread(ItemField.Source, originalSource != sourceToEdit)
        setReferencePreviewOnUIThread()
    }


    private fun editContent() {
        contentToEdit?.let { content ->
            val editHtmlTextDialog = EditHtmlTextDialog()

            editHtmlTextDialog.showDialog(supportFragmentManager, content, R.string.activity_edit_entry_edit_content_title) {
                appliedChangesToContent(it)
            }
        }
    }

    private fun appliedChangesToContent(content: String) {
        contentToEdit = content

        runOnUiThread {
            updateEntryFieldChangedOnUIThread(ItemField.Content, originalInformation != contentToEdit)
            setContentPreviewOnUIThread()
        }
    }

    private fun abstractChanged(didAbstractChange: Boolean) {
        abstractToEdit = lytAbstractPreview.getCurrentFieldValue()
        entryPropertySet()

        runOnUiThread {
            updateEntryFieldChangedOnUIThread(ItemField.TitleOrSummary, didAbstractChange)
        }
    }

    private fun appliedChangesToAbstract(didAbstractChange: Boolean) {
        abstractToEdit = lytAbstractPreview.getCurrentFieldValue()
        entryPropertySet()

        runOnUiThread {
            updateEntryFieldChangedOnUIThread(ItemField.TitleOrSummary, didAbstractChange)
            setAbstractPreviewOnUIThread()
            mayShowSaveEntryChangesHelpOnUIThread()
        }
    }

    private fun editReference() {
        setWaitingForResult(EditReferenceActivity.ResultId)

        val reference = sourceToEdit
        val entry = item ?: readLaterArticle?.itemExtractionResult?.item ?: itemExtractionResult?.item ?: Item("") // should never be the case that item is null, just to make compiler happy

        presenter.editReference(reference, entry, getCurrentSeries())
    }

    private fun appliedChangesToReference(result: EditReferenceActivityResult) {
        if(result.didSaveReference) {
            result.savedSource?.let { savedReference(it) }

            mayShowSaveEntryChangesHelpOnUIThread()
        }
        else if(result.didDeleteReference) {
            referenceCleared()
        }

        entryPropertySet()
    }

    private fun editTagsOnEntry() {
        val tagsOnEntryDialog = TagsOnEntryDialogFragment()

        tagsOnEntryDialog.show(supportFragmentManager, tagsOnEntry) {
            appliedChangesToTags(it)
        }
    }

    private fun appliedChangesToTags(editedTags: Collection<Tag>) {
        tagsOnEntry.clear()
        tagsOnEntry.addAll(editedTags)
        entryPropertySet()

        runOnUiThread {
            updateEntryFieldChangedOnUIThread(ItemField.Tags, didTagsChange(editedTags))
            setTagsOnEntryPreviewOnUIThread()
            mayShowSaveEntryChangesHelpOnUIThread()
        }
    }

    private fun didTagsChange(editedTags: Collection<Tag>): Boolean {
        originalTags?.let { originalTags ->
            if(originalTags.size != editedTags.size) {
                return true
            }

            val copy = ArrayList(originalTags)
            copy.removeAll(editedTags)

            return copy.size > 0
        }

        return true
    }

    private fun updateEntryFieldChangedOnUIThread(field: ItemField, didChange: Boolean) {
        if(didChange) {
            changedFields.add(field)
        }
        else {
            changedFields.remove(field)
        }

        setMenuSaveEntryVisibleStateOnUIThread()
    }

    private fun setMenuSaveEntryVisibleStateOnUIThread() {
        if(haveAllFieldsOfExistingEntryBeenDeleted()) {
            mnSaveEntry?.isVisible = false
            mnDeleteExistingEntry?.isVisible = true
        }
        else {
            mnSaveEntry?.isVisible = item == null // ItemExtractionResult and ReadLaterArticle always can be saved
                    || changedFields.size > 0
            mnDeleteExistingEntry?.isVisible = false
        }
    }

    private fun haveAllFieldsOfExistingEntryBeenDeleted(): Boolean {
        if(item != null && item?.isPersisted() == true) {
            return contentToEdit.isNullOrBlank() && tagsOnEntry.isEmpty() && sourceToEdit == null && abstractToEdit.isNullOrBlank()
        }

        return false
    }

    private fun entryPropertySet() {
        val localSettings = entryService.dataManager.localSettings

        if(localSettings.didShowAddItemPropertiesHelp == false && contentToEdit.isNullOrBlank() == false) {
            localSettings.didShowAddItemPropertiesHelp = true
            entryService.dataManager.localSettingsUpdated()
        }
    }


    private fun setContentPreviewOnUIThread() {
        setContentPreviewOnUIThread(sourceToEdit)
    }

    private fun setContentPreviewOnUIThread(source: Source?) {
        val content = contentToEdit
        val url = source?.url
        var showContentOnboarding = true
        prgIsLoadingWebPage.visibility = View.GONE

        if(shouldShowContent(content)) {
            showContentInWebView(content, url)
            showContentOnboarding = false
        }
        else if(isInReaderMode == false && webSiteHtml != null) {
            showContentInWebView(webSiteHtml, url)
            showContentOnboarding = false
        }
        else if(url != null && item == null) { // then load url (but don't show it for an Item)
            clearWebViewEntry()
            isLoadingUrl = true
            wbvwContent.elementClickedListener = { true } // disable link clicks during loading url
            wbvwContent.setWebViewClient(WebViewClient()) // to avoid that redirects open url in browser
            prgIsLoadingWebPage.visibility = View.VISIBLE
            wbvwContent.loadUrl(url)
            showContentOnboarding = false
        }

        setOnboardingTextAndFloatingActionButtonVisibilityOnUIThread(showContentOnboarding)
    }

    private fun shouldShowContent(content: String?): Boolean {
        // TODO: currently we assume that for item content is always set, this may change in the feature
        return content.isNullOrBlank() == false &&
                (item != null || (isInReaderMode &&
                        (itemExtractionResult?.couldExtractContent == true || readLaterArticle?.itemExtractionResult?.couldExtractContent == true)) )
    }

    private fun showContentInWebView(contentParam: String?, url: String?) {
        var content = contentParam

        if(content?.startsWith("<html") == false && content?.startsWith("<body") == false && content?.startsWith("<!doctype") == false) {
            content = "<html><body style=\"font-family: serif, Georgia, Roboto, Helvetica, Arial; font-size:17;\">" + content + "</body></html>"
        }

        clearWebViewEntry() // clear WebView
        if(url != null && Build.VERSION.SDK_INT > 16) {
            wbvwContent.loadDataWithBaseURL(url, content, "text/html; charset=UTF-8", "utf-8", null)
        }
        else {
            wbvwContent.loadData(content, "text/html; charset=UTF-8", null)
        }
    }

    private fun clearWebViewEntry() {
        if(Build.VERSION.SDK_INT < 18) {
            wbvwContent.clearView()
        }
        else {
            wbvwContent.loadUrl("about:blank")
        }
    }

    private fun setOnboardingTextVisibilityOnUIThread(showContentOnboarding: Boolean? = null) {
        val showOnboardingForEntryProperties = shouldShowOnboardingForEntryProperties()
        if(showContentOnboarding == true || showOnboardingForEntryProperties) {
            lytOnboardingText.visibility = View.VISIBLE
            lytContentWebViewAndOnboardingText.setOnClickListener { editContent() } // only enable editing content by clicking on lytContentWebViewAndOnboardingText when showing onboarding text

            val onboardingTextId = if(showContentOnboarding == true) R.string.activity_edit_entry_edit_content_onboarding_text else R.string.activity_edit_entry_add_entry_properties_onboarding_text
            val onboardingText = if(showContentOnboarding == true) getText(onboardingTextId).toString() else getText(onboardingTextId).toString()
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                txtOnboardingText.text = Html.fromHtml(onboardingText, Html.FROM_HTML_MODE_LEGACY)
            }
            else {
                txtOnboardingText.text = Html.fromHtml(onboardingText)
            }

            arrowToFloatingActionButton.visibility = if(showContentOnboarding != true && showOnboardingForEntryProperties) View.VISIBLE else View.GONE
        }
        else {
            lytOnboardingText.visibility = View.GONE
            lytContentWebViewAndOnboardingText.setOnClickListener(null)
        }

        if(showContentOnboarding == true) {
            lytContentWebView.visibility = View.GONE
        }
        else if(showContentOnboarding == false) {
            lytContentWebView.visibility = View.VISIBLE
        }
    }

    private fun shouldShowOnboardingForEntryProperties(): Boolean {
        return entryService.dataManager.localSettings.didShowAddItemPropertiesHelp == false &&
                lytTagsPreview.visibility == View.GONE && lytReferencePreview.visibility == View.GONE && lytAbstractPreview.visibility == View.GONE
    }


    private fun setAbstractPreviewOnUIThread() {
        if(abstractToEdit.isNullOrBlank()) {
//            lytAbstractPreview.setOnboardingTextOnUiThread(R.string.activity_edit_entry_abstract_onboarding_text)
        }
        else {
            lytAbstractPreview.setFieldValueOnUiThread(abstractToEdit.getPlainTextForHtml())
        }

        val showAbstractPreview = this.forceShowAbstractPreview || abstractToEdit.isNullOrBlank() == false

        lytAbstractPreview.visibility = if(showAbstractPreview) View.VISIBLE else View.GONE
        if(fabEditEntryAbstract.visibility != View.INVISIBLE) { // visibility already set by FloatingActionMenu
            fabEditEntryAbstract.visibility = if(showAbstractPreview) View.GONE else View.VISIBLE
        }
        setOnboardingTextAndFloatingActionButtonVisibilityOnUIThread()
    }

    private fun setReferencePreviewOnUIThread() {
        if(sourceToEdit == null) {
            lytReferencePreview.setOnboardingTextOnUiThread(R.string.activity_edit_entry_reference_onboarding_text)
        }
        else {
            lytReferencePreview.setFieldValueOnUiThread(sourceToEdit.getPreviewWithSeriesAndPublishingDate(getCurrentSeries()))
        }

        val showReferencePreview = this.forceShowReferencePreview || sourceToEdit != null

        lytReferencePreview.visibility = if(showReferencePreview) View.VISIBLE else View.GONE
        if(fabEditEntryReference.visibility != View.INVISIBLE) { // visibility already set by FloatingActionMenu
            fabEditEntryReference.visibility = if(showReferencePreview) View.GONE else View.VISIBLE
        }
        setOnboardingTextAndFloatingActionButtonVisibilityOnUIThread()

        btnClearEntryReference.visibility = if(sourceToEdit == null) View.GONE else View.VISIBLE
        mnShareEntry?.isVisible = sourceToEdit?.url.isNullOrBlank() == false
    }

    private fun getCurrentSeries(): Series? {
        readLaterArticle?.let { return it.itemExtractionResult.series }

        itemExtractionResult?.let { return it.series }

        return sourceToEdit?.series
    }

    private fun setTagsOnEntryPreviewOnUIThread() {
        if(tagsOnEntry.filterNotNull().isEmpty()) {
            lytTagsPreview.setOnboardingTextOnUiThread(R.string.activity_edit_entry_tags_onboarding_text)
        }
        else {
            val tagsPreview = tagsOnEntry.filterNotNull().sortedBy { it.name.toLowerCase() }.joinToString { it.name }
            lytTagsPreview.setFieldValueOnUiThread(tagsPreview)
        }

        val showTagsPreview = this.forceShowTagsPreview || tagsOnEntry.size > 0

        lytTagsPreview.visibility = if(showTagsPreview) View.VISIBLE else View.GONE
        if(fabEditEntryTags.visibility != View.INVISIBLE) { // visibility already set by FloatingActionMenu
            fabEditEntryTags.visibility = if (showTagsPreview) View.GONE else View.VISIBLE
        }
        setOnboardingTextAndFloatingActionButtonVisibilityOnUIThread()
    }

    private fun setOnboardingTextAndFloatingActionButtonVisibilityOnUIThread(showContentOnboarding: Boolean? = null) {
        val calculatedShowContentOnboarding = if(showContentOnboarding == null) lytContentWebView.visibility != View.VISIBLE else showContentOnboarding
        setOnboardingTextVisibilityOnUIThread(calculatedShowContentOnboarding)
        setFloatingActionButtonVisibilityOnUIThread()
    }

    private fun setFloatingActionButtonVisibilityOnUIThread() {
        // when user comes to EditEntryDialog, don't show floatingActionMenu till some content has been entered. She/he should focus on the content
        val hasUserEverEnteredSomeContent = dataManager.localSettings.didShowAddItemPropertiesHelp || contentToEdit.isNullOrBlank() == false

        floatingActionMenu.setVisibilityOnUIThread(wbvwContent.isInFullscreenMode, hasUserEverEnteredSomeContent)
    }


    private fun handleChangeFullscreenModeEvent(mode: FullscreenWebView.FullscreenMode) {
        when(mode) {
            FullscreenWebView.FullscreenMode.Enter -> enterFullscreenMode()
            FullscreenWebView.FullscreenMode.Leave -> leaveFullscreenMode()
        }
    }

    private fun handleWebViewSingleTap(isInFullscreen: Boolean) {
        if(isInFullscreen == false) {
            editContent()
        }
    }

    private fun handleWebViewSwipe(isInFullscreen: Boolean, swipeDirection: OnSwipeTouchListener.SwipeDirection) {
        if(isInFullscreen) {
            when(swipeDirection) {
                OnSwipeTouchListener.SwipeDirection.Left -> {
                    mayShowEntryInformationFullscreenGesturesHelpOnUIThread { presenter.returnToPreviousView() }
                }
                OnSwipeTouchListener.SwipeDirection.Right -> {
                    mayShowEntryInformationFullscreenGesturesHelpOnUIThread { editTagsOnEntry() }
                }
            }
        }
    }

    private fun mayShowEntryInformationFullscreenGesturesHelpOnUIThread(userConfirmedHelpOnUIThread: () -> Unit) {
        val localSettings = entryService.dataManager.localSettings

        if(localSettings.didShowItemInformationFullscreenGesturesHelp == false) {
            dialogService.showConfirmationDialog(getString(R.string.context_help_entry_content_fullscreen_gestures), showNoButton = false) {
                runOnUiThread {
                    wbvwContent.leaveFullscreenMode() // leave fullscreen otherwise a lot of unwanted behaviour occurs
                    userConfirmedHelpOnUIThread()
                }
            }

            localSettings.didShowItemInformationFullscreenGesturesHelp = true
            entryService.dataManager.localSettingsUpdated()
        }
        else {
            wbvwContent.leaveFullscreenMode() // leave fullscreen otherwise a lot of unwanted behaviour occurs
            userConfirmedHelpOnUIThread()
        }
    }


    private fun leaveFullscreenMode() {
        lytEntryFieldsPreview.visibility = View.VISIBLE
        txtEntryContentLabel.visibility = View.VISIBLE
        appBarLayout.visibility = View.VISIBLE
        setOnboardingTextAndFloatingActionButtonVisibilityOnUIThread()
    }

    private fun enterFullscreenMode() {
        lytEntryFieldsPreview.visibility = View.GONE
        txtEntryContentLabel.visibility = View.GONE
        lytOnboardingText.visibility = View.GONE
        appBarLayout.visibility = View.GONE
        setFloatingActionButtonVisibilityOnUIThread()

        content_layout_root.invalidate()

        mayShowEntryInformationFullscreenHelpOnUIThread()
    }

    private fun mayShowEntryInformationFullscreenHelpOnUIThread() {
        val localSettings = entryService.dataManager.localSettings

        if(localSettings.didShowItemInformationFullscreenHelp == false) {
            contextHelpUtil.showContextHelp(lytContextHelpFullscreenMode, R.string.context_help_entry_content_fullscreen)

            localSettings.didShowItemInformationFullscreenHelp = true
            entryService.dataManager.localSettingsUpdated()
        }
    }


    override fun onPause() {
        unregisterEventBusListener()

        super.onPause()
    }

    override fun onDestroy() {
        pauseWebView()

        parameterHolder.clearActivityResults(EditReferenceActivity.ResultId)

        super.onDestroy()
    }

    private fun pauseWebView() {
        // to prevent that a video keeps on playing in WebView when navigating away from ViewEntryActivity
        // see https://stackoverflow.com/a/6230902
        try {
            Class.forName("android.webkit.WebView")
                    .getMethod("onPause")
                    .invoke(wbvwContent)

        } catch(ignored: Exception) { }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if(floatingActionMenu.handlesTouch(event)) { // close menu when menu is opened and touch is outside floatingActionMenuButton
            return true
        }

        return super.dispatchTouchEvent(event)
    }

    override fun onBackPressed() {
        if(isEditEntryFieldDialogVisible()) { // let TagEntriesListDialog handle back button press
            super.onBackPressed()
            return
        }
        else if(floatingActionMenu.handlesBackButtonPress()) {
            return
        }

        askIfUnsavedChangesShouldBeSavedAndCloseDialog()
    }

    private fun isEditEntryFieldDialogVisible(): Boolean {
        return supportFragmentManager.findFragmentByTag(EditHtmlTextDialog.TAG) != null || supportFragmentManager.findFragmentByTag(TagsOnEntryDialogFragment.TAG) != null
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_edit_entry_menu, menu)
        toolbarUtil.setupActionItemsLayout(menu) { menuItem -> onOptionsItemSelected(menuItem) }

        mnSaveEntry = menu.findItem(R.id.mnSaveEntry)
        mnDeleteExistingEntry = menu.findItem(R.id.mnDeleteExistingEntry)

        mnToggleReaderMode = menu.findItem(R.id.mnToggleReaderMode)
        mnToggleReaderMode?.isVisible = itemExtractionResult?.couldExtractContent == true || readLaterArticle?.itemExtractionResult?.couldExtractContent == true /*&& webSiteHtml != null*/ // show mnToggleReaderMode only if previously original web site was shown
        setReaderModeActionStateOnUIThread()

        mnSaveEntryExtractionResultForLaterReading = menu.findItem(R.id.mnSaveEntryExtractionResultForLaterReading)
        mnSaveEntryExtractionResultForLaterReading?.isVisible = itemExtractionResult != null

        mnDeleteReadLaterArticle = menu.findItem(R.id.mnDeleteReadLaterArticle)
        mnDeleteReadLaterArticle?.isVisible = readLaterArticle != null

        mnShareEntry = menu.findItem(R.id.mnShareEntry)
        mnShareEntry?.isVisible = sourceToEdit?.url.isNullOrBlank() == false

        setMenuSaveEntryVisibleStateOnUIThread()

        return true
    }

    private fun setReaderModeActionStateOnUIThread() {
        mnToggleReaderMode?.let { mnToggleReaderMode ->
            if(mnToggleReaderMode.isVisible == true) {
                if(isInReaderMode) {
                    mnToggleReaderMode.title = getString(R.string.action_website_view)
                    mnToggleReaderMode.setIcon(R.drawable.ic_reader_mode_disabled)
                }
                else {
                    mnToggleReaderMode.title = getString(R.string.action_reader_view)
                    mnToggleReaderMode.setIcon(R.drawable.ic_reader_mode)
                }

                toolbarUtil.updateMenuItemView(mnToggleReaderMode)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            android.R.id.home -> {
                askIfUnsavedChangesShouldBeSavedAndCloseDialog()
                return true
            }
            R.id.mnSaveEntry -> {
                saveEntryAndCloseDialog()
                return true
            }
            R.id.mnDeleteExistingEntry -> {
                askIfShouldDeleteExistingEntryAndCloseDialog()
                return true
            }
            R.id.mnToggleReaderMode -> {
                toggleReaderMode()
                return true
            }
            R.id.mnSaveEntryExtractionResultForLaterReading -> {
                saveEntryExtrationResultForLaterReadingAndCloseDialog()
                return true
            }
            R.id.mnDeleteReadLaterArticle -> {
                deleteReadLaterArticleAndCloseDialog()
                return true
            }
            R.id.mnShareEntry -> {
                showShareEntryPopupMenu()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    private fun toggleReaderMode() {
        isInReaderMode = !isInReaderMode

        if(isInReaderMode) {
            val extractionResult = itemExtractionResult ?: readLaterArticle?.itemExtractionResult
            contentToEdit = extractionResult?.item?.content ?: ""
        }
        else {
            contentToEdit = webSiteHtml ?: ""
        }

        setContentPreviewOnUIThread()
        invalidateOptionsMenu()
    }

    private fun showShareEntryPopupMenu() {
        val overflowMenuButton = getOverflowMenuButton()
        if(overflowMenuButton == null) {
            return
        }

        val popup = PopupMenu(this, overflowMenuButton)

        popup.menuInflater.inflate(R.menu.share_entry_menu, popup.menu)

        val reference = sourceToEdit
        if(reference == null || reference.url.isNullOrBlank()) {
            popup.menu.findItem(R.id.mnShareEntryReferenceUrl).isVisible = false
        }

        popup.setOnMenuItemClickListener { item ->
            when(item.itemId) {
                R.id.mnShareEntryReferenceUrl -> shareReferenceUrl()
                R.id.mnShareEntryContent -> shareEntryContent()
            }
            true
        }

        popup.show()
    }

    private fun getOverflowMenuButton(): View? {
        for(i in 0..toolbar.childCount - 1) { // OverflowMenuButton is child of ActionMenuView which is child of toolbar (both don't have an id so i cannot search for them)
            val child = toolbar.getChildAt(i)

            if(child is ActionMenuView) {
                for(j in 0..child.childCount) {
                    val actionMenuViewChild = child.getChildAt(j)

                    if(actionMenuViewChild is AppCompatImageView && actionMenuViewChild is ActionMenuView.ActionMenuChildView) {
                        return actionMenuViewChild
                    }
                }
            }
        }

        return null
    }

    private fun shareReferenceUrl() {
        sourceToEdit?.let { reference ->
            presenter.shareReferenceUrl(reference)
        }
    }

    private fun shareEntryContent() {
        item?.let { entry ->
            presenter.shareEntry(entry, entry.source, entry.source?.series)
        }

        readLaterArticle?.itemExtractionResult?.let { extractionResult ->
            presenter.shareEntry(extractionResult.item, extractionResult.source, extractionResult.series)
        }

        itemExtractionResult?.let { extractionResult ->
            presenter.shareEntry(extractionResult.item, extractionResult.source, extractionResult.series)
        }
    }


    private fun saveEntryAndCloseDialog() {
        mnSaveEntry?.isEnabled = false // disable to that save cannot be pressed a second time
        mnSaveEntryExtractionResultForLaterReading?.isEnabled = false
        unregisterEventBusListener()

        if(lytAbstractPreview.hasFocus()) { // OnFocusChangeListener doesn't get called when save action gets pressed
            appliedChangesToAbstract(lytAbstractPreview.didValueChange)
        }

        saveEntryAsync { successful ->
            if(successful) {
                mayShowSavedReadLaterArticleHelpAndCloseDialog()
            }
            else {
                mnSaveEntry?.isEnabled = true
                mnSaveEntryExtractionResultForLaterReading?.isEnabled = true
                mayRegisterEventBusListener()
            }
        }
    }

    private fun saveEntryAsync(callback: (Boolean) -> Unit) {
        val content = contentToEdit ?: ""
        val abstract = abstractToEdit ?: ""

        item?.let { entry ->
            updateEntry(entry, content, abstract)
            presenter.saveEntryAsync(entry, sourceToEdit, sourceToEdit?.series, tags = tagsOnEntry) { successful ->
                if(successful) {
                    setActivityResult(EditEntryActivityResult(didSaveEntry = true, savedItem = entry))
                }
                callback(successful)
            }
        }

        itemExtractionResult?.let { extractionResult ->
            // TODO: save extracted content when in reader mode and webSiteHtml when not in reader mode
            // TODO: contentToEdit show now always contain the correct value depending on is or is not in reader mode, doesn't it?

            updateEntry(extractionResult.item, content, abstract)
            presenter.saveEntryAsync(extractionResult.item, sourceToEdit, extractionResult.series, tagsOnEntry) { successful ->
                if(successful) {
                    setActivityResult(EditEntryActivityResult(didSaveEntryExtractionResult = true, savedItem = extractionResult.item))
                }
                callback(successful)
            }
        }

        readLaterArticle?.let { readLaterArticle ->
            val extractionResult = readLaterArticle.itemExtractionResult
            updateEntry(extractionResult.item, content, abstract)

            presenter.saveEntryAsync(extractionResult.item, sourceToEdit, extractionResult.series, tagsOnEntry) { successful ->
                if(successful) {
                    readLaterArticleService.delete(readLaterArticle)
                    setActivityResult(EditEntryActivityResult(didSaveReadLaterArticle = true, savedItem = extractionResult.item))
                }
                callback(successful)
            }
        }
    }

    private fun mayShowSavedReadLaterArticleHelpAndCloseDialog() {
        mayShowSavedReadLaterArticleHelp {
            runOnUiThread {
                closeDialog()
            }
        }
    }

    private fun mayShowSavedReadLaterArticleHelp(callback: () -> Unit) {
        val localSettings = entryService.dataManager.localSettings

        if(readLaterArticle != null && localSettings.didShowSavedReadLaterArticleIsNowInItemsHelp == false) {
            localSettings.didShowSavedReadLaterArticleIsNowInItemsHelp = true
            entryService.dataManager.localSettingsUpdated()

            dialogService.showConfirmationDialog(getString(R.string.context_help_saved_read_later_article_is_now_in_entries), showNoButton = false) {
                callback()
            }
        }
        else {
            callback()
        }
    }

    private fun saveEntryExtrationResultForLaterReadingAndCloseDialog() {
        mnSaveEntry?.isEnabled = false // disable to that save cannot be pressed a second time
        mnSaveEntryExtractionResultForLaterReading?.isEnabled = false
        unregisterEventBusListener()

        saveEntryForLaterReading { successful ->
            if(successful) {
                runOnUiThread { closeDialog() }
            }
            else {
                mnSaveEntry?.isEnabled = true
                mnSaveEntryExtractionResultForLaterReading?.isEnabled = true
                mayRegisterEventBusListener()
            }
        }
    }

    private fun saveEntryForLaterReading(callback: (Boolean) -> Unit) {
        val content = contentToEdit ?: ""
        val abstract = abstractToEdit ?: ""

        itemExtractionResult?.let { extractionResult ->
            updateEntry(extractionResult.item, content, abstract)
            extractionResult.source = sourceToEdit
            extractionResult.tags = tagsOnEntry

            presenter.saveEntryExtractionResultForLaterReading(extractionResult)
            setActivityResult(EditEntryActivityResult(didSaveEntryExtractionResult = true, savedItem = extractionResult.item))
            callback(true)
        }

        if(itemExtractionResult == null) {
            callback(false)
        }
    }


    private fun askIfShouldDeleteExistingEntryAndCloseDialog() {
        item?.let { entry ->
            dialogService.showConfirmationDialog(getString(R.string.activity_edit_entry_alert_message_delete_entry, entry.entryPreview)) { deleteEntry ->
                if(deleteEntry) {
                    mnDeleteExistingEntry?.isEnabled = false
                    unregisterEventBusListener()

                    deleteEntityService.deleteEntry(entry)
                    closeDialog()
                }
            }
        }
    }

    private fun deleteReadLaterArticleAndCloseDialog() {
        readLaterArticle?.let { readLaterArticle ->
            mnSaveEntry?.isEnabled = false // disable to that save cannot be pressed a second time
            mnDeleteReadLaterArticle?.isEnabled = false
            unregisterEventBusListener()

            presenter.deleteReadLaterArticle(readLaterArticle)

            runOnUiThread { closeDialog() }
        }
    }


    private fun setActivityResult(result: EditEntryActivityResult) {
        parameterHolder.setActivityResult(ResultId, result)
    }

    private fun updateEntry(item: Item, content: String, abstract: String) {
        item.content = content
        item.summary = abstract
    }


    private fun askIfUnsavedChangesShouldBeSavedAndCloseDialog() {
        if(changedFields.size > 0) {
            askIfUnsavedChangesShouldBeSaved()
        }
        else {
            closeDialog()
        }
    }

    private fun askIfUnsavedChangesShouldBeSaved() {
        dialogService.showConfirmationDialog(getString(R.string.activity_edit_entry_alert_message_entry_contains_unsaved_changes)) { shouldChangesGetSaved ->
            runOnUiThread {
                if(shouldChangesGetSaved) {
                    saveEntryAndCloseDialog()
                }
                else {
                    closeDialog()
                }
            }
        }
    }

    private fun closeDialog() {
        finish()
    }


    private fun showParameters(parameters: EditEntryActivityParameters?) {
        if(parameters != null) {
            parameters.item?.let { editEntry(it) }

            parameters.readLaterArticle?.let { editReadLaterArticle(it) }

            parameters.itemExtractionResult?.let {
                isInReaderMode = it.couldExtractContent
                editEntryExtractionResult(it)
            }

            if(parameters.createEntry) {
                createEntry()
            }
        }
    }

    private fun createEntry(editContent: Boolean = true) {
        editEntry(Item(""))

        if(editContent) {
            editContent() // go directly to edit content dialog, there's absolutely nothing to see on this almost empty screen
        }
    }

    private fun editEntry(entryId: String) {
        entryService.retrieve(entryId)?.let { entry ->
            editEntry(entry)
        }
    }

    private fun editEntry(item: Item) {
        this.item = item

        editEntry(item, item.source, item.tags)
    }

    private fun editReadLaterArticle(readLaterArticleId: String) {
        readLaterArticleService.retrieve(readLaterArticleId)?.let { readLaterArticle ->
            editReadLaterArticle(readLaterArticle)
        }
    }

    private fun editReadLaterArticle(readLaterArticle: ReadLaterArticle, updateContentPreview: Boolean = true) {
        this.readLaterArticle = readLaterArticle

        editEntry(readLaterArticle.itemExtractionResult.item, readLaterArticle.itemExtractionResult.source, readLaterArticle.itemExtractionResult.tags, updateContentPreview)
    }

    private fun editEntryExtractionResult(serializedExtractionResult: String) {
        restoreSerializedObjectFromDisk(serializedExtractionResult, ItemExtractionResult::class.java)?.let { extractionResult ->
            editEntryExtractionResult(extractionResult)
        }
    }

    private fun editEntryExtractionResult(extractionResult: ItemExtractionResult, updateContentPreview: Boolean = true) {
        this.itemExtractionResult = extractionResult

        editEntry(itemExtractionResult?.item, itemExtractionResult?.source, itemExtractionResult?.tags, updateContentPreview)
    }

    private fun editEntry(item: Item?, source: Source?, tags: MutableCollection<Tag>?, updateContentPreview: Boolean = true) {
        originalInformation = item?.content
        originalTags = tags
        originalSource = source
        originalTitleAbstract = item?.summary

        contentToEdit = item?.content
        abstractToEdit = item?.summary
        sourceToEdit = source

        if(abstractToEdit.isNullOrBlank() == false) { this.forceShowAbstractPreview = true } // forcing that once it has been shown it doesn't get hidden anymore

        source?.let { this.forceShowReferencePreview = true } // forcing that once it has been shown it doesn't get hidden anymore

        tags?.forEach { tag ->
            if(tagsOnEntry.contains(tag) == false) { // to avoid have a tag twice we really have to check each single tag
                tagsOnEntry.add(tag)
            }
        }

        if(tags?.size ?: 0 > 0) {
            forceShowTagsPreview = true
        }

        updateDisplayedValuesOnUIThread(source, updateContentPreview)
    }

    private fun updateDisplayedValuesOnUIThread(source: Source? = sourceToEdit, updateContentPreview: Boolean = true) {
        if(updateContentPreview) {
            setContentPreviewOnUIThread(source)
        }

        setTagsOnEntryPreviewOnUIThread()

        setReferencePreviewOnUIThread()

        setAbstractPreviewOnUIThread()
    }

    private fun restoreReference(referenceId: String) {
        sourceToEdit = referenceService.retrieve(referenceId)

        runOnUiThread { setReferencePreviewOnUIThread() }
    }

    private fun restoreTagsOnEntryAsync(tagsOnEntryIdsString: String) {
        threadPool.runAsync { restoreTagsOnEntry(tagsOnEntryIdsString) }
    }

    private fun restoreTagsOnEntry(tagsOnEntryIdsString: String) {
        val restoredTagsOnEntry = serializer.deserializeObject(tagsOnEntryIdsString, List::class.java, Tag::class.java) as List<Tag>

        tagsOnEntry.clear()
        tagsOnEntry.addAll(restoredTagsOnEntry)

        runOnUiThread { setTagsOnEntryPreviewOnUIThread() }
    }


    private fun mayRegisterEventBusListener() {
        if(item?.isPersisted() ?: false && eventBusListener == null) {
            synchronized(this) {
                val eventBusListenerInit = EventBusListener()

                eventBus.register(eventBusListenerInit)

                this.eventBusListener = eventBusListenerInit
            }
        }
    }

    private fun unregisterEventBusListener() {
        synchronized(this) {
            eventBusListener?.let {
                eventBus.unregister(it)
            }

            this.eventBusListener = null
        }
    }

    private fun warnEntryHasBeenEdited(item: Item) {
        unregisterEventBusListener() // message now gets shown, don't display it a second time

        runOnUiThread {
            dialogService.showInfoMessage(getString(R.string.activity_edit_entry_alert_message_entry_has_been_edited))
        }
    }

    private fun updateDisplayedValues() {
        runOnUiThread { updateDisplayedValuesOnUIThread() }
    }

    inner class EventBusListener {

        @Handler
        fun entryChanged(change: EntryChanged) {
            if(change.entity.id == item?.id && change.isDependentChange == false) {
                if(change.source == EntityChangeSource.Synchronization) {
                    warnEntryHasBeenEdited(change.entity)
                }
                else {
                    updateDisplayedValues()
                }
            }
        }
    }

}
