package net.dankito.deepthought.android

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.TextView
import kotlinx.android.synthetic.main.view_floating_action_button_main.*
import me.zhanghai.android.effortlesspermissions.EffortlessPermissions
import me.zhanghai.android.effortlesspermissions.OpenAppDetailsDialogFragment
import net.dankito.deepthought.android.activities.BaseActivity
import net.dankito.deepthought.android.activities.ReadLaterArticlesListViewActivity
import net.dankito.deepthought.android.activities.SourcesListViewActivity
import net.dankito.deepthought.android.activities.TagsListViewActivity
import net.dankito.deepthought.android.di.AppComponent
import net.dankito.deepthought.android.dialogs.FileChooserDialog
import net.dankito.deepthought.android.dialogs.TagsListViewDialog
import net.dankito.deepthought.android.fragments.EntriesListView
import net.dankito.deepthought.android.service.ExtractArticleHandler
import net.dankito.deepthought.android.service.IntentHandler
import net.dankito.deepthought.android.views.MainActivityFloatingActionMenuButton
import net.dankito.deepthought.model.LocalSettings
import net.dankito.deepthought.news.summary.config.ArticleSummaryExtractorConfigManager
import net.dankito.deepthought.service.data.DataManager
import net.dankito.deepthought.ui.IRouter
import net.dankito.service.eventbus.IEventBus
import net.dankito.utils.UrlUtil
import org.slf4j.LoggerFactory
import pub.devrel.easypermissions.AfterPermissionGranted
import javax.inject.Inject


class MainActivity : BaseActivity() {

    companion object {
        private const val REQUEST_CODE_OPEN_FILE_PERMISSION = 1
        private val PERMISSIONS_OPEN_FILE = arrayOf<String>(Manifest.permission.WRITE_EXTERNAL_STORAGE)

        private val log = LoggerFactory.getLogger(MainActivity::class.java)
    }


    private lateinit var floatingActionMenuButton: MainActivityFloatingActionMenuButton

    protected lateinit var drawerToggle: ActionBarDrawerToggle

    private lateinit var entriesListView: EntriesListView

    private var fileChooserDialog: FileChooserDialog? = null

    private var localSettingsChangedListener: ((LocalSettings) -> Unit)? = null


    @Inject
    protected lateinit var router: IRouter

    @Inject
    protected lateinit var urlUtil: UrlUtil

    @Inject
    protected lateinit var eventBus: IEventBus

    @Inject
    protected lateinit var dataManager: DataManager

    @Inject
    protected lateinit var summaryExtractorManager: ArticleSummaryExtractorConfigManager

    @Inject
    protected lateinit var extractArticleHandler: ExtractArticleHandler


    init {
        AppComponent.component.inject(this)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupUI()

        dataManager.addInitializationListener { initializedDataManager() }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        handleIntent(intent)
    }


    private fun setupUI() {
        setContentView(R.layout.activity_main)

        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)

        val drawer = findViewById(R.id.drawer_layout) as DrawerLayout
        drawerToggle = ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        val navigationView = findViewById(R.id.nav_view) as NavigationView
        showAppVersion(navigationView)
        navigationView.setNavigationItemSelectedListener(navigationListener)

        entriesListView = supportFragmentManager.findFragmentByTag("EntriesListView") as EntriesListView

        floatingActionMenuButton = MainActivityFloatingActionMenuButton(floatingActionMenu, summaryExtractorManager, router, eventBus)
    }

    private fun showAppVersion(navigationView: NavigationView) {
        try {
            val packageInfo = this.packageManager.getPackageInfo(packageName, 0)
            val version = packageInfo.versionName
            (navigationView.getHeaderView(0).findViewById(R.id.txtAppVersion) as? TextView)?.text = version
        } catch (e: Exception) {
            log.error("Could not read application version")
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if(floatingActionMenuButton.handlesTouch(event)) { // close menu when menu is opened and touch is outside floatingActionMenuButton
            return true
        }

        return super.dispatchTouchEvent(event)
    }

    private fun initializedDataManager() {
        runOnUiThread {
            drawerToggle.isDrawerIndicatorEnabled = dataManager.localSettings.didUserCreateDataEntity // only show hamburger icon when user already has created at least one entity
        }

        if(dataManager.localSettings.didUserCreateDataEntity == false) {
            listenToDidUserCreateDataEntityChanges()
        }
    }

    private fun listenToDidUserCreateDataEntityChanges() {
        val listener: (LocalSettings) -> Unit = { localSettings ->
            if(localSettings.didUserCreateDataEntity) {
                localSettingsChangedListener?.let { dataManager.removeLocalSettingsChangedListener(it) }
                localSettingsChangedListener = null

                userCreatedDataEntity()
            }
        }

        dataManager.addLocalSettingsChangedListener(listener)
        this.localSettingsChangedListener = listener
    }

    private fun userCreatedDataEntity() {
        runOnUiThread {
            drawerToggle.isDrawerIndicatorEnabled = true
        }
    }


    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)

        floatingActionMenuButton.saveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)

        floatingActionMenuButton.restoreInstanceState(savedInstanceState)
    }


    override fun onResume() {
        super.onResume()

        clearAllActivityResults() // important, so that the results from Activities opened from one of the tabs aren't displayed later in another activity (e.g. opening
        // EditReferenceActivity from ReferenceListView tab first, then going to EditEntryActivity -> Source of first called EditReferenceActivity is then shown in second EditEntryActivity
    }

    override fun onBackPressed() {
        if(floatingActionMenuButton.handlesBackButtonPress()) {

        }
        else if(dialogHandlesBackButton() == false && entriesListView.onBackPressed() == false) {
            super.onBackPressed() // when not handling by fragment call default back button press handling
        }
    }

    private fun dialogHandlesBackButton(): Boolean {
        val tagsListViewDialog = supportFragmentManager.findFragmentByTag(TagsListViewDialog.Tag) as? TagsListViewDialog
        if(tagsListViewDialog != null) {
            return tagsListViewDialog.handlesBackButtonPress()
        }

        return false
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Dispatch to our library.
        EffortlessPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private val navigationListener = NavigationView.OnNavigationItemSelectedListener { item ->
        when (item.itemId) {
            R.id.navTags -> navigateToActivity(TagsListViewActivity::class.java)

            R.id.navSources -> navigateToActivity(SourcesListViewActivity::class.java)

            R.id.navReadLaterArticles -> navigateToActivity(ReadLaterArticlesListViewActivity::class.java)

            R.id.navArticleSummaryExtractors -> {
                router.showArticleSummaryExtractorsView()
            }

            R.id.navImportPdf -> importPdf()
        }

        val drawer = findViewById(R.id.drawer_layout) as DrawerLayout
        drawer.closeDrawer(GravityCompat.START)

        true
    }

    @AfterPermissionGranted(REQUEST_CODE_OPEN_FILE_PERMISSION)
    private fun importPdf() {
        if(EffortlessPermissions.hasPermissions(this, *PERMISSIONS_OPEN_FILE)) {
            // We've got the permission.
            importPdfWithPermission()
        }
        else if(EffortlessPermissions.somePermissionPermanentlyDenied(this, *PERMISSIONS_OPEN_FILE)) {
            // Some permission is permanently denied so we cannot request them normally.
            OpenAppDetailsDialogFragment.show(
                    R.string.open_file_permission_request_message,
                    R.string.open_settings, this)
        }
        else  {
            // Request the permissions.
            EffortlessPermissions.requestPermissions(this,
                    R.string.open_file_permission_request_message,
                    REQUEST_CODE_OPEN_FILE_PERMISSION, *PERMISSIONS_OPEN_FILE)
        }

    }

    private fun importPdfWithPermission() {
        if(fileChooserDialog == null) {
            fileChooserDialog = FileChooserDialog(this)
        }

        fileChooserDialog?.selectFile { file ->
            router.showPdfView(file)
        }
    }

    // TODO: move to Router
    private fun navigateToActivity(activityClass: Class<out BaseActivity>, parameters: Any? = null) {
        val intent = Intent(this, activityClass)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        parameters?.let { addParametersToIntent(intent, parameters) }

        startActivity(intent)
    }


    private fun addParametersToIntent(intent: Intent, parameters: Any) {
        val id = parameterHolder.setParameters(parameters)

        intent.putExtra(BaseActivity.ParametersId, id)
    }


    private fun handleIntent(intent: Intent?) {
        if(intent == null) {
            return
        }

        IntentHandler(extractArticleHandler, router, urlUtil).handle(intent)
    }

}