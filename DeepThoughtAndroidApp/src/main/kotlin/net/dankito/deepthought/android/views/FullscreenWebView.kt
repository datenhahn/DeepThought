package net.dankito.deepthought.android.views

import android.app.Activity
import android.content.Context
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import kotlinx.android.synthetic.main.activity_edit_entry.view.*
import net.dankito.deepthought.android.R
import net.dankito.deepthought.android.extensions.getColorFromResourceId
import net.dankito.deepthought.android.service.OnSwipeTouchListener
import net.dankito.richtexteditor.android.command.ToolbarCommandStyle
import net.dankito.richtexteditor.android.toolbar.SearchView
import net.dankito.richtexteditor.android.toolbar.SearchViewStyle
import java.util.*


/**
 * On scroll down fires a listener to enter full screen mode, on scroll up fires the same listener to leave full screen mode.
 */
class FullscreenWebView : WebView {

    enum class FullscreenMode {
        Enter,
        Leave
    }


    companion object {
        private const val DefaultScrollDownDifferenceYThreshold = 3
        private const val DefaultScrollUpDifferenceYThreshold = -10

        private const val AfterTogglingNotHandleScrollEventsForMillis = 500

        private const val SCROLL_X_BUNDLE_NAME = "SCROLL_X"
        private const val SCROLL_Y_BUNDLE_NAME = "SCROLL_Y"


        private const val NON_FULLSCREEN_MODE_SYSTEM_UI_FLAGS = 0
        private val FULLSCREEN_MODE_SYSTEM_UI_FLAGS: Int


        init {
            FULLSCREEN_MODE_SYSTEM_UI_FLAGS = createFullscreenModeSystemUiFlags()
        }

        private fun createFullscreenModeSystemUiFlags(): Int {
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


    var isInFullscreenMode = false

    var scrollUpDifferenceYThreshold = DefaultScrollUpDifferenceYThreshold
    var scrollDownDifferenceYThreshold = DefaultScrollDownDifferenceYThreshold

    var changeFullscreenModeListener: ((FullscreenMode) -> Unit)? = null

    private var leftFullscreenCallback: (() -> Unit)? = null

    var singleTapListener: ((isInFullscreen: Boolean) -> Unit)? = null

    var doubleTapListener: ((isInFullscreen: Boolean) -> Unit)? = null

    var swipeListener: ((isInFullscreen: Boolean, OnSwipeTouchListener.SwipeDirection) -> Unit)? = null

    /**
     * Should return true if Android's url loading should be disabled
     */
    var elementClickedListener: ((elementType: Int) -> Boolean)? = null


    private var hasReachedEnd = false

    private var disableScrolling = false

    private var lastOnScrollFullscreenModeTogglingTimestamp: Date? = null

    private lateinit var swipeTouchListener: OnSwipeTouchListener

    private var lytFullscreenWebViewOptionsBar: ViewGroup? = null

    private lateinit var searchView: SearchView

    private var isSearchViewVisible = false

    private var checkIfScrollingStoppedTimer = Timer()

    private var checkIfScrollingStoppedTimerTask: TimerTask? = null

    private var scrollPositionToRestore: Point? = null


    constructor(context: Context) : super(context) { setupUI(context) }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { setupUI(context) }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { setupUI(context) }


    private fun setupUI(context: Context) {
        swipeTouchListener = OnSwipeTouchListener(context) { handleWebViewSwipe(it) }

        swipeTouchListener.singleTapListener = { handleWebViewSingleTap() }
        swipeTouchListener.doubleTapListener = { handleWebViewDoubleTap() }
    }


    fun setOptionsBar(lytFullscreenWebViewOptionsBar: ViewGroup) {
        this.lytFullscreenWebViewOptionsBar = lytFullscreenWebViewOptionsBar

        lytFullscreenWebViewOptionsBar.btnLeaveFullscreen.setOnClickListener { leaveFullscreenMode() }

        searchView = SearchView(context)
        lytFullscreenWebViewOptionsBar.addView(searchView, 0)

        val width = context.resources.getDimension(R.dimen.fullscreen_web_view_options_bar_button_width) / context.resources.displayMetrics.density
        val backgroundColor = context.getColorFromResourceId(R.color.colorPrimary)
        searchView.applyStyle(SearchViewStyle(ToolbarCommandStyle(widthDp = width.toInt()), backgroundColor, 16f))
        searchView.webView = this

        searchView.searchViewExpandedListener = { isExpanded ->
            if(isExpanded) {
                isSearchViewVisible = true
            }
            else {
                isSearchViewVisible = false

                this.systemUiVisibility = FULLSCREEN_MODE_SYSTEM_UI_FLAGS
            }
        }
    }


    override fun onWindowSystemUiVisibilityChanged(flags: Int) {
        if(flags == 0) {
            isInFullscreenMode = false // otherwise isInFullscreenMode stays true and full screen mode isn't entered anymore on resume
        }

        // as immersive fullscreen is only available for KitKat and above leave immersive fullscreen mode by swiping from screen top or bottom is also only available on these  devices
        if(flags != FULLSCREEN_MODE_SYSTEM_UI_FLAGS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if(isSearchViewVisible == false) {
                leaveFullscreenMode()

                leftFullscreenCallback?.invoke()
                leftFullscreenCallback = null
            }
            else {
                searchView.postDelayed({
                    this.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                }, 250)
            }
        }

        super.onWindowSystemUiVisibilityChanged(flags)
    }

    /**
     * WebView doesn't fire click event, so we had to implement this our self
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        swipeTouchListener.onTouch(this, event)

        if(event.action == MotionEvent.ACTION_UP && elementClickedListener != null) {
            hitTestResult?.let { hitResult ->
                val type = hitResult.type

                elementClickedListener?.let { return it.invoke(type) } // this is bad: in most cases type is UNKNOWN, even though clicked on images etc. -> we cannot determine if user clicked an element or simply the background
            }
        }

        if(disableScrolling) { // if both taps of a double tap weren't exactly on the same place may a large scroll occur after transition to fullscreen / not-fullscreen mode -> disable scrolling during this time
            return event.action == MotionEvent.ACTION_MOVE || event.action == MotionEvent.ACTION_UP // somehow we also have to catch the last ACTION_UP as otherwise text gets selected
        }

        return super.onTouchEvent(event)
    }

    private fun handleWebViewSingleTap() {
        val type = hitTestResult?.type

        // leave the functionality for clicking on links, phone numbers, geo coordinates, ... Only go to fullscreen mode when clicked somewhere else in the WebView or on an image
        if(type == null || type == WebView.HitTestResult.UNKNOWN_TYPE || type == WebView.HitTestResult.IMAGE_TYPE) {
            singleTapListener?.invoke(isInFullscreenMode)
        }
    }

    private fun handleWebViewDoubleTap() {
        disableScrolling = true // otherwise double tap may triggers a large scroll

        if(isInFullscreenMode) {
            leaveFullscreenMode()
        }
        else {
            enterFullscreenMode()
        }

        doubleTapListener?.invoke(isInFullscreenMode)

        postDelayed({
            disableScrolling = false
        }, 300)
    }

    private fun handleWebViewSwipe(swipeDirection: OnSwipeTouchListener.SwipeDirection) {
        swipeListener?.invoke(isInFullscreenMode, swipeDirection)
    }


    override fun onScrollChanged(scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int) {
        super.onScrollChanged(scrollX, scrollY, oldScrollX, oldScrollY)

        if(hasFullscreenModeToggledShortlyBefore()) {
            return // when toggling reader mode there's a huge jump in scroll difference due to displaying additional / hiding controls -> filter out these events shortly after  entering/leaving reader mode
        }

        val differenceY = scrollY - oldScrollY

        if(isInFullscreenMode == false) {
            checkShouldEnterFullscreenMode(differenceY)
        }

        val tolerance = computeVerticalScrollExtent() / 10
        this.hasReachedEnd = scrollY >= computeVerticalScrollRange() - computeVerticalScrollExtent() - tolerance

        hideOptionsBarOnUiThread()
        startCheckIfScrollingStopped()
    }

    private fun checkShouldEnterFullscreenMode(differenceY: Int) {
        if(differenceY > scrollDownDifferenceYThreshold || differenceY < scrollUpDifferenceYThreshold) {
            enterFullscreenMode()
        }
    }

    private fun startCheckIfScrollingStopped() {
        checkIfScrollingStoppedTimerTask?.cancel()

        checkIfScrollingStoppedTimerTask = object: TimerTask() {
            override fun run() {
                showOptionsBar()
            }
        }

        checkIfScrollingStoppedTimer.schedule(checkIfScrollingStoppedTimerTask, 300)
    }

    private fun hideOptionsBarOnUiThread() {
        lytFullscreenWebViewOptionsBar?.visibility = View.GONE
    }

    private fun showOptionsBar() {
        (lytFullscreenWebViewOptionsBar?.context as? Activity)?.let { activity ->
            activity.runOnUiThread {
                showOptionsBarOnUiThread()
            }
        }
    }

    private fun showOptionsBarOnUiThread() {
        lytFullscreenWebViewOptionsBar?.visibility = View.VISIBLE
    }


    private fun enterFullscreenMode() {
        isInFullscreenMode = true
        updateLastOnScrollFullscreenModeTogglingTimestamp()
        showOptionsBar()

        changeFullscreenModeListener?.invoke(FullscreenMode.Enter)

        this.systemUiVisibility = FULLSCREEN_MODE_SYSTEM_UI_FLAGS
    }


    fun leaveFullscreenModeAndWaitTillLeft(leftFullscreenCallback: () -> Unit) {
        this.leftFullscreenCallback = leftFullscreenCallback

        leaveFullscreenMode()
    }

    private fun leaveFullscreenMode() {
        isInFullscreenMode = false
        updateLastOnScrollFullscreenModeTogglingTimestamp()
        hideOptionsBarOnUiThread()
        searchView.hideSearchControls()

        changeFullscreenModeListener?.invoke(FullscreenMode.Leave)

        this.systemUiVisibility = NON_FULLSCREEN_MODE_SYSTEM_UI_FLAGS

        if(hasReachedEnd) {
            scrollToEndDelayed()
        }
    }


    private fun hasFullscreenModeToggledShortlyBefore(): Boolean {
        return Date().time - (lastOnScrollFullscreenModeTogglingTimestamp?.time ?: 0) < AfterTogglingNotHandleScrollEventsForMillis
    }


    private fun scrollToEndDelayed() {
        postDelayed({
            scrollToEnd()
        }, 50)
    }

    private fun scrollToEnd() {
        updateLastOnScrollFullscreenModeTogglingTimestamp() // we also have to set lastOnScrollFullscreenModeTogglingTimestamp as otherwise scrolling may is large enough to re-enter fullscreen mode
        scrollY = computeVerticalScrollRange() - computeVerticalScrollExtent()
        updateLastOnScrollFullscreenModeTogglingTimestamp() // to be on the safe side
    }


    fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(SCROLL_X_BUNDLE_NAME, scrollX)
        outState.putInt(SCROLL_Y_BUNDLE_NAME, scrollY)
    }

    fun restoreInstanceState(savedInstanceState: Bundle) {
        scrollPositionToRestore = Point(savedInstanceState.getInt(SCROLL_X_BUNDLE_NAME), savedInstanceState.getInt(SCROLL_Y_BUNDLE_NAME))
    }


    /*      Ensure that a scroll due to loadData() doesn't toggle Fullscreen        */

    override fun loadData(data: String?, mimeType: String?, encoding: String?) {
        updateLastOnScrollFullscreenModeTogglingTimestamp()
        super.loadData(data, mimeType, encoding)

        mayRestoreScrollPosition()
    }

    override fun loadDataWithBaseURL(baseUrl: String?, data: String?, mimeType: String?, encoding: String?, historyUrl: String?) {
        updateLastOnScrollFullscreenModeTogglingTimestamp()
        super.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl)

        mayRestoreScrollPosition()
    }

    override fun loadUrl(url: String?) {
        updateLastOnScrollFullscreenModeTogglingTimestamp()
        super.loadUrl(url)

        mayRestoreScrollPosition()
    }

    override fun loadUrl(url: String?, additionalHttpHeaders: MutableMap<String, String>?) {
        updateLastOnScrollFullscreenModeTogglingTimestamp()
        super.loadUrl(url, additionalHttpHeaders)

        mayRestoreScrollPosition()
    }

    private fun updateLastOnScrollFullscreenModeTogglingTimestamp() {
        lastOnScrollFullscreenModeTogglingTimestamp = Date()
    }

    private fun mayRestoreScrollPosition() {
        postDelayed({ // older devices need a delay as otherwise scroll position gets applied before content is loaded (and therefore scroll view grew to its extend)
            scrollPositionToRestore?.let {
                scrollX = it.x
                scrollY = it.y

                scrollPositionToRestore = null
            }
        }, 250)
    }

}
