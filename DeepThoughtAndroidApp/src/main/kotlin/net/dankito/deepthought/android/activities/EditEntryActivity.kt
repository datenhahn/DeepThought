package net.dankito.deepthought.android.activities

import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.widget.RelativeLayout
import kotlinx.android.synthetic.main.activity_edit_entry.*
import net.dankito.deepthought.android.R
import net.dankito.deepthought.android.activities.arguments.EditEntryActivityResult
import net.dankito.deepthought.android.activities.arguments.EditReferenceActivityResult
import net.dankito.deepthought.android.activities.arguments.EntryActivityParameters
import net.dankito.deepthought.android.di.AppComponent
import net.dankito.deepthought.android.dialogs.EditHtmlTextDialog
import net.dankito.deepthought.android.dialogs.TagsOnEntryDialogFragment
import net.dankito.deepthought.android.service.OnSwipeTouchListener
import net.dankito.deepthought.android.views.EntryFieldsPreview
import net.dankito.deepthought.android.views.FullScreenWebView
import net.dankito.deepthought.model.*
import net.dankito.deepthought.model.util.EntryExtractionResult
import net.dankito.deepthought.ui.IRouter
import net.dankito.deepthought.ui.html.HtmlEditorCommon
import net.dankito.deepthought.ui.html.IHtmlEditorListener
import net.dankito.deepthought.ui.presenter.EditEntryPresenter
import net.dankito.deepthought.ui.presenter.util.EntryPersister
import net.dankito.service.data.EntryService
import net.dankito.service.data.ReadLaterArticleService
import net.dankito.service.data.TagService
import net.dankito.service.data.messages.EntryChanged
import net.dankito.service.eventbus.IEventBus
import net.dankito.utils.IThreadPool
import net.dankito.utils.serialization.ISerializer
import net.dankito.utils.ui.IDialogService
import net.engio.mbassy.listener.Handler
import java.util.*
import javax.inject.Inject


class EditEntryActivity : BaseActivity() {

    companion object {
        private const val ENTRY_ID_INTENT_EXTRA_NAME = "ENTRY_ID"
        private const val READ_LATER_ARTICLE_ID_INTENT_EXTRA_NAME = "READ_LATER_ARTICLE_ID"
        private const val ENTRY_EXTRACTION_RESULT_INTENT_EXTRA_NAME = "ENTRY_EXTRACTION_RESULT"

        private const val CONTENT_INTENT_EXTRA_NAME = "CONTENT"
        private const val ABSTRACT_INTENT_EXTRA_NAME = "ABSTRACT"
        private const val TAGS_ON_ENTRY_INTENT_EXTRA_NAME = "TAGS_ON_ENTRY"

        const val ResultId = "EDIT_ENTRY_ACTIVITY_RESULT"

        private const val NON_READER_MODE_SYSTEM_UI_FLAGS = 0
        private val READER_MODE_SYSTEM_UI_FLAGS: Int


        init {
            READER_MODE_SYSTEM_UI_FLAGS = createReaderModeSystemUiFlags()
        }

        private fun createReaderModeSystemUiFlags(): Int {
            // see https://developer.android.com/training/system-ui/immersive.html
            var flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
                // even thought View.SYSTEM_UI_FLAG_FULLSCREEN is also available from SDK 16 and above, to my experience it doesn't work reliable (at least not on Android 4.1)
                flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            }

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                flags = flags or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE
            }

            return flags
        }
    }


    @Inject
    protected lateinit var entryService: EntryService

    @Inject
    protected lateinit var readLaterArticleService: ReadLaterArticleService

    @Inject
    protected lateinit var tagService: TagService

    @Inject
    protected lateinit var entryPersister: EntryPersister

    @Inject
    protected lateinit var router: IRouter

    @Inject
    protected lateinit var threadPool: IThreadPool

    @Inject
    protected lateinit var serializer: ISerializer

    @Inject
    protected lateinit var dialogService: IDialogService

    @Inject
    protected lateinit var eventBus: IEventBus


    private var entry: Entry? = null

    private var readLaterArticle: ReadLaterArticle? = null

    private var entryExtractionResult: EntryExtractionResult? = null


    private var contentToEdit: String? = null

    private var abstractToEdit: String? = null

    private var referenceToEdit: Reference? = null

    private val tagsOnEntry: MutableList<Tag> = ArrayList()

    private var canEntryBeSaved = false

    private var entryHasBeenEdited = false


    private val presenter: EditEntryPresenter

    private var isInReaderMode = false

    private lateinit var swipeTouchListener: OnSwipeTouchListener


    private lateinit var entryFieldsPreview: EntryFieldsPreview

    private var mnSaveEntry: MenuItem? = null


    private var eventBusListener: EventBusListener? = null


    init {
        AppComponent.component.inject(this)

        presenter = EditEntryPresenter(entryPersister, router)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        parameterHolder.setActivityResult(ResultId, EditEntryActivityResult())

        setupUI()

        savedInstanceState?.let { restoreState(it) }

        showParameters(getParameters() as? EntryActivityParameters)
    }

    private fun restoreState(savedInstanceState: Bundle) {
        savedInstanceState.getString(ENTRY_EXTRACTION_RESULT_INTENT_EXTRA_NAME)?.let { editEntryExtractionResult(it) }
        savedInstanceState.getString(READ_LATER_ARTICLE_ID_INTENT_EXTRA_NAME)?.let { readLaterArticleId -> editReadLaterArticle(readLaterArticleId) }
        savedInstanceState.getString(ENTRY_ID_INTENT_EXTRA_NAME)?.let { entryId -> editEntry(entryId) }

        savedInstanceState.getString(CONTENT_INTENT_EXTRA_NAME)?.let { content ->
            contentToEdit = content
            setContentPreviewOnUIThread()
        }

        savedInstanceState.getString(ABSTRACT_INTENT_EXTRA_NAME)?.let { abstract ->
            abstractToEdit = abstract
            setAbstractPreviewOnUIThread()
        }

        savedInstanceState.getString(TAGS_ON_ENTRY_INTENT_EXTRA_NAME)?.let { tagsOnEntryIds -> restoreTagsOnEntryAsync(tagsOnEntryIds) }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)

        outState?.let {
            outState.putString(ENTRY_ID_INTENT_EXTRA_NAME, null)
            entry?.id?.let { entryId -> outState.putString(ENTRY_ID_INTENT_EXTRA_NAME, entryId) }

            outState.putString(READ_LATER_ARTICLE_ID_INTENT_EXTRA_NAME, null)
            readLaterArticle?.id?.let { readLaterArticleId -> outState.putString(READ_LATER_ARTICLE_ID_INTENT_EXTRA_NAME, readLaterArticleId) }

            outState.putString(ENTRY_EXTRACTION_RESULT_INTENT_EXTRA_NAME, null)
            entryExtractionResult?.let { outState.putString(ENTRY_EXTRACTION_RESULT_INTENT_EXTRA_NAME, serializer.serializeObject(it)) }

            outState.putString(TAGS_ON_ENTRY_INTENT_EXTRA_NAME, serializer.serializeObject(tagsOnEntry))

            outState.putString(CONTENT_INTENT_EXTRA_NAME, contentToEdit)

            outState.putString(ABSTRACT_INTENT_EXTRA_NAME, abstractToEdit)
        }
    }


    private fun setupUI() {
        setContentView(R.layout.activity_edit_entry)

        setSupportActionBar(toolbar)

        supportActionBar?.let { actionBar ->
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setDisplayShowHomeEnabled(true)

            actionBar.title = ""
        }

        this.entryFieldsPreview = lytEntryFieldsPreview
        entryFieldsPreview.fieldClickedListener = { field -> entryFieldClicked(field)}

        setupEntryContentView()
    }

    private fun setupEntryContentView() {
        wbEntry.setOnSystemUiVisibilityChangeListener { flags -> systemUiVisibilityChanged(flags) }
        wbEntry.changeFullScreenModeListener = { mode -> handleChangeFullScreenModeEvent(mode) }

        swipeTouchListener = OnSwipeTouchListener(this) { handleWebViewSwipe(it) }
        swipeTouchListener.singleTapListener = { handleWebViewClick() }
        swipeTouchListener.doubleTapListener = { handleWebViewDoubleTap() }

        wbEntry.setOnTouchListener { _, event -> handleWebViewTouch(event) }

        val settings = wbEntry.getSettings()
        settings.defaultTextEncodingName = "UTF-8" // otherwise non ASCII text doesn't get displayed correctly
        settings.defaultFontSize = 18 // default font is too small
        settings.domStorageEnabled = true // otherwise images may not load, see https://stackoverflow.com/questions/29888395/images-not-loading-in-android-webview
        settings.javaScriptEnabled = true // so that embedded videos etc. work
    }


    override fun onResume() {
        super.onResume()

        (getAndClearResult(EditReferenceActivity.ResultId) as? EditReferenceActivityResult)?.let { result ->
            if(result.didSaveReference) {
                result.savedReference?.let { savedReference(it) }
            }
        }
    }

    private fun savedReference(reference: Reference) {
        referenceToEdit = reference // do not set reference directly on entry as if entry is not saved yet adding it to reference.entries causes an error
        entryFieldsPreview.reference = reference

        updateCanEntryBeSavedOnUIThread(true)
        setReferencePreviewOnUIThread()
    }


    private fun entryFieldClicked(field: EntryField) {
        when(field) {
            EntryField.Abstract -> editAbstract()
            EntryField.Reference -> editReference()
            EntryField.Tags -> editTagsOnEntry()
        }
    }

    private fun editContent() {
        contentToEdit?.let { content ->
            val editHtmlTextDialog = EditHtmlTextDialog()

            editHtmlTextDialog.showDialog(supportFragmentManager, content) {
                contentToEdit = it

                entryHasBeenEdited()

                runOnUiThread {
                    updateCanEntryBeSavedOnUIThread(true)
                    setContentPreviewOnUIThread()
                }
            }
        }
    }

    private fun editAbstract() {
        abstractToEdit?.let { abstract ->
            val editHtmlTextDialog = EditHtmlTextDialog()

            editHtmlTextDialog.showDialog(supportFragmentManager, abstract) {
                abstractToEdit = it

                entryHasBeenEdited()

                runOnUiThread {
                    updateCanEntryBeSavedOnUIThread(true)
                    setAbstractPreviewOnUIThread()
                }
            }
        }
    }

    private fun editReference() {
        setWaitingForResult(EditReferenceActivity.ResultId)

        val reference = referenceToEdit

        if(reference != null) {
            presenter.editReference(reference)
        }
        else {
            presenter.createReference()
        }
    }

    private fun editTagsOnEntry() {
        val tagsOnEntryDialog = TagsOnEntryDialogFragment()

        tagsOnEntryDialog.show(supportFragmentManager, tagsOnEntry) {
            tagsOnEntry.clear()
            tagsOnEntry.addAll(it)

            entryHasBeenEdited()

            runOnUiThread {
                updateCanEntryBeSavedOnUIThread(true)
                setTagsOnEntryPreviewOnUIThread()
            }
        }
    }

    private fun updateCanEntryBeSavedOnUIThread(canEntryBeSaved: Boolean) {
        this.canEntryBeSaved = canEntryBeSaved

        setMenuSaveEntryEnabledStateOnUIThread()
    }

    private fun setMenuSaveEntryEnabledStateOnUIThread() {
        mnSaveEntry?.isEnabled = canEntryBeSaved
    }


    private fun setContentPreviewOnUIThread() {
        setContentPreviewOnUIThread(referenceToEdit)
    }

    private fun setContentPreviewOnUIThread(reference: Reference?) {
        var content = contentToEdit
        val url = reference?.url

        if(content?.startsWith("<html") == false && content?.startsWith("<body") == false) {
            content = "<body style=\"font-family: serif, Georgia, Roboto, Helvetica, Arial; font-size:17;\"" + content + "</body>"
        }

        if(url != null && Build.VERSION.SDK_INT > 16) {
            wbEntry.loadDataWithBaseURL(url, content, "text/html; charset=UTF-8", "utf-8", null)
        }
        else {
            wbEntry.loadData(content, "text/html; charset=UTF-8", null)
        }
    }

    private fun setAbstractPreviewOnUIThread() {
        entryFieldsPreview.setAbstractPreviewOnUIThread()
    }

    private fun setReferencePreviewOnUIThread() {
        entryFieldsPreview.setReferencePreviewOnUIThread()
    }

    private fun setTagsOnEntryPreviewOnUIThread() {
        entryFieldsPreview.setTagsOnEntryPreviewOnUIThread()
    }


    private fun systemUiVisibilityChanged(flags: Int) {
        // as immersive fullscreen is only available for KitKat and above leave immersive fullscreen mode by swiping from screen top or bottom is also only available on these  devices
        if(flags == NON_READER_MODE_SYSTEM_UI_FLAGS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            leaveReaderMode()
        }
    }

    /**
     * WebView doesn't fire click event, so we had to implement this our self
     */
    private fun handleWebViewTouch(event: MotionEvent): Boolean {
        swipeTouchListener.onTouch(wbEntry, event)

        return false // don't consume event as otherwise scrolling won't work anymore
    }

    private fun handleWebViewClick() {
        val hitResult = wbEntry.hitTestResult
        val type = hitResult.type

        // leave the functionality for clicking on links, phone numbers, geo coordinates, ... Only go to reader mode when clicked somewhere else in the WebView or on an image
        if(type == WebView.HitTestResult.UNKNOWN_TYPE || type == WebView.HitTestResult.IMAGE_TYPE) {
            if(isInReaderMode) {
                leaveReaderMode()
            }
            else {
                editContent()
            }
        }
    }

    private fun handleChangeFullScreenModeEvent(mode: FullScreenWebView.FullScreenMode) {
        when(mode) {
            FullScreenWebView.FullScreenMode.Enter -> enterReaderMode()
            FullScreenWebView.FullScreenMode.Leave -> leaveReaderMode()
        }
    }

    private fun handleWebViewDoubleTap() {
        if(isInReaderMode) {
            saveEntryAndCloseDialog()
        }
    }

    private fun handleWebViewSwipe(swipeDirection: OnSwipeTouchListener.SwipeDirection) {
        if(isInReaderMode) {
            when(swipeDirection) {
                OnSwipeTouchListener.SwipeDirection.Left -> presenter.returnToPreviousView()
                OnSwipeTouchListener.SwipeDirection.Right -> editTagsOnEntry()
            }
        }
    }

    private fun leaveReaderMode() {
        isInReaderMode = false

        entryFieldsPreview.visibility = View.VISIBLE
        appBarLayout.visibility = View.VISIBLE

        val layoutParams = wbEntry.layoutParams as RelativeLayout.LayoutParams
        layoutParams.alignWithParent = false
        wbEntry.layoutParams = layoutParams

        wbEntry.systemUiVisibility = NON_READER_MODE_SYSTEM_UI_FLAGS
    }

    private fun enterReaderMode() {
        isInReaderMode = true

        entryFieldsPreview.visibility = View.GONE
        appBarLayout.visibility = View.GONE

        val layoutParams = wbEntry.layoutParams as RelativeLayout.LayoutParams
        layoutParams.alignWithParent = true
        wbEntry.layoutParams = layoutParams

        wbEntry.systemUiVisibility = READER_MODE_SYSTEM_UI_FLAGS
    }


    override fun onDestroy() {
        pauseWebView()

        parameterHolder.clearActivityResults(EditReferenceActivity.ResultId)

        unregisterEventBusListener()

        super.onDestroy()
    }

    private fun pauseWebView() {
        // to prevent that a video keeps on playing in WebView when navigating away from ViewEntryActivity
        // see https://stackoverflow.com/a/6230902
        try {
            Class.forName("android.webkit.WebView")
                    .getMethod("onPause")
                    .invoke(wbEntry)

        } catch(ignored: Exception) { }
    }

    override fun onBackPressed() {
        if(isEditEntryFieldDialogVisible()) { // let TagEntriesListDialog handle back button press
            super.onBackPressed()
            return
        }

        askIfUnsavedChangesShouldBeSavedAndCloseDialog()
    }

    private fun isEditEntryFieldDialogVisible(): Boolean {
        return supportFragmentManager.findFragmentByTag(EditHtmlTextDialog.TAG) != null || supportFragmentManager.findFragmentByTag(TagsOnEntryDialogFragment.TAG) != null
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_edit_entry_menu, menu)

        mnSaveEntry = menu.findItem(R.id.mnSaveEntry)

        setMenuSaveEntryEnabledStateOnUIThread()

        return true
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
        }

        return super.onOptionsItemSelected(item)
    }


    private fun saveEntryAndCloseDialog() {
        mnSaveEntry?.isEnabled = false // disable to that save cannot be pressed a second time
        unregisterEventBusListener()

        saveEntryAsync { successful ->
            if(successful) {
                runOnUiThread { closeDialog() }
            }
            else {
                mnSaveEntry?.isEnabled = true
                mayRegisterEventBusListener()
            }
        }
    }

    private fun saveEntryAsync(callback: (Boolean) -> Unit) {
        val content = contentToEdit ?: ""
        val abstract = abstractToEdit ?: ""

        entry?.let { entry ->
            updateEntry(entry, content, abstract)
            presenter.saveEntryAsync(entry, referenceToEdit, tagsOnEntry) { successful ->
                if(successful) {
                    setActivityResult(EditEntryActivityResult(didSaveEntry = true, savedEntry = entry))
                }
                callback(successful)
            }
        }

        entryExtractionResult?.let { extractionResult ->
            updateEntry(extractionResult.entry, content, abstract)
            presenter.saveEntryAsync(extractionResult.entry, referenceToEdit, tagsOnEntry) { successful ->
                if(successful) {
                    setActivityResult(EditEntryActivityResult(didSaveEntryExtractionResult = true, savedEntry = extractionResult.entry))
                }
                callback(successful)
            }
        }

        readLaterArticle?.let { readLaterArticle ->
            val extractionResult = readLaterArticle.entryExtractionResult
            updateEntry(extractionResult.entry, content, abstract)

            presenter.saveEntryAsync(extractionResult.entry, referenceToEdit, tagsOnEntry) { successful ->
                if(successful) {
                    readLaterArticleService.delete(readLaterArticle)
                    setActivityResult(EditEntryActivityResult(didSaveReadLaterArticle = true, savedEntry = extractionResult.entry))
                }
                callback(successful)
            }
        }
    }

    private fun setActivityResult(result: EditEntryActivityResult) {
        parameterHolder.setActivityResult(ResultId, result)
    }

    private fun updateEntry(entry: Entry, content: String, abstract: String) {
        entry.content = content
        entry.abstractString = abstract
    }


    private fun askIfUnsavedChangesShouldBeSavedAndCloseDialog() {
        if(entryHasBeenEdited) {
            askIfUnsavedChangesShouldBeSaved()
        }
        else {
            closeDialog()
        }
    }

    private fun askIfUnsavedChangesShouldBeSaved() {
        dialogService.showConfirmationDialog(getString(R.string.activity_edit_entry_alert_message_entry_contains_unsaved_changes)) { shouldChangedGetSaved ->
            runOnUiThread {
                if(shouldChangedGetSaved) {
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


    private fun showParameters(parameters: EntryActivityParameters?) {
        if(parameters == null) { // create entry
            if(entry == null) { // entry != null -> entry has been restored from savedInstanceState, parameters therefor is null
                createEntry()
            }
        }
        else {
            parameters.entry?.let { editEntry(it) }

            parameters.readLaterArticle?.let { editReadLaterArticle(it) }

            parameters.entryExtractionResult?.let { editEntryExtractionResult(it) }

            parameters.field?.let { entryFieldClicked(it) }
        }
    }

    private fun createEntry() {
        canEntryBeSaved = true

        editEntry(Entry(""))
        editContent() // go directly to edit content dialog, there's absolutely nothing to see on this almost empty screen
    }

    private fun editEntry(entryId: String) {
        entryService.retrieve(entryId)?.let { entry ->
            editEntry(entry)
        }
    }

    private fun editEntry(entry: Entry) {
        this.entry = entry
        entryFieldsPreview.entry = entry

        editEntry(entry, entry.reference, entry.tags)
    }

    private fun editReadLaterArticle(readLaterArticleId: String) {
        readLaterArticleService.retrieve(readLaterArticleId)?.let { readLaterArticle ->
            editReadLaterArticle(readLaterArticle)
        }
    }

    private fun editReadLaterArticle(readLaterArticle: ReadLaterArticle) {
        this.readLaterArticle = readLaterArticle
        entryFieldsPreview.readLaterArticle = readLaterArticle
        canEntryBeSaved = true

        editEntry(readLaterArticle.entryExtractionResult.entry, readLaterArticle.entryExtractionResult.reference, readLaterArticle.entryExtractionResult.tags)
    }

    private fun editEntryExtractionResult(serializedExtractionResult: String) {
        val extractionResult = serializer.deserializeObject(serializedExtractionResult, EntryExtractionResult::class.java)

        editEntryExtractionResult(extractionResult)
    }

    private fun editEntryExtractionResult(extractionResult: EntryExtractionResult) {
        this.entryExtractionResult = extractionResult
        entryFieldsPreview.entryExtractionResult = this.entryExtractionResult
        canEntryBeSaved = true

        editEntry(entryExtractionResult?.entry, entryExtractionResult?.reference, entryExtractionResult?.tags)
    }

    private fun editEntry(entry: Entry?, reference: Reference?, tags: Collection<Tag>?) {
        contentToEdit = entry?.content
        abstractToEdit = entry?.abstractString
        referenceToEdit = reference

        entryFieldsPreview.reference = reference

        setContentPreviewOnUIThread(reference)

        setAbstractPreviewOnUIThread()

        setReferencePreviewOnUIThread()

        tags?.let {
            tagsOnEntry.addAll(tags)
            entryFieldsPreview.tagsOnEntry = tagsOnEntry

            setTagsOnEntryPreviewOnUIThread()
        }

        mayRegisterEventBusListener()
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


    private fun contentHasBeenEdited() {
        entryHasBeenEdited()
        runOnUiThread { updateCanEntryBeSavedOnUIThread(true) }
    }

    private fun entryHasBeenEdited() {
        entryHasBeenEdited = true
    }


    private val contentListener = object : IHtmlEditorListener {

        override fun editorHasLoaded(editor: HtmlEditorCommon) {
        }

        override fun htmlCodeUpdated() {
            contentHasBeenEdited()
        }

        override fun htmlCodeHasBeenReset() {
        }

    }


    private fun mayRegisterEventBusListener() {
        if(entry?.isPersisted() ?: false) {
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

    private fun entryHasBeenEdited(entry: Entry) {
        unregisterEventBusListener() // message now gets shown, don't display it a second time

        runOnUiThread {
            dialogService.showInfoMessage(getString(R.string.activity_edit_entry_alert_message_entry_has_been_edited))
        }
    }

    inner class EventBusListener {

        @Handler
        fun entryChanged(change: EntryChanged) {
            if(change.entity == entry) {
                entryHasBeenEdited(change.entity)
            }
        }
    }

}
