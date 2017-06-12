package net.dankito.deepthought.android

import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import kotlinx.android.synthetic.main.content_main.*
import net.dankito.deepthought.android.adapter.EntryAdapter
import net.dankito.deepthought.android.di.AppComponent
import net.dankito.deepthought.android.dialogs.ArticleSummaryExtractorsDialog
import net.dankito.deepthought.android.routing.Router
import net.dankito.deepthought.model.Entry
import net.dankito.deepthought.service.data.DataManager
import net.dankito.service.data.messages.EntitiesOfTypeChanged
import net.dankito.service.eventbus.IEventBus
import net.dankito.service.search.ISearchEngine
import net.dankito.service.search.specific.EntriesSearch
import net.engio.mbassy.listener.Handler
import javax.inject.Inject
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    @Inject
    protected lateinit var dataManager: DataManager

    @Inject
    protected lateinit var searchEngine: ISearchEngine

    @Inject
    protected lateinit var router: Router

    @Inject
    protected lateinit var eventBus: IEventBus

    private val eventBusListener = EventBusListener()


    private val entryAdapter = EntryAdapter()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupUI()

        setupDataAsync()
    }

    override fun onDestroy() {
        eventBus.unregister(eventBusListener)

        super.onDestroy()
    }

    private fun setupUI() {
        setContentView(R.layout.activity_main)

        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)

        val fab = findViewById(R.id.fab) as FloatingActionButton
        fab.setOnClickListener { view -> floatingActionButtonClicked() }

//        val drawer = findViewById(R.id.drawer_layout) as DrawerLayout
//        val toggle = ActionBarDrawerToggle(
//                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
//        drawer.addDrawerListener(toggle)
//        toggle.syncState()

        val navigationView = findViewById(R.id.nav_view) as NavigationView
        navigationView.setNavigationItemSelectedListener(this)

        lstEntries.adapter = entryAdapter
        lstEntries.setOnItemClickListener { _, _, position, _ -> clickedOnEntry(entryAdapter.getItem(position)) }
    }

    private fun floatingActionButtonClicked() {
        val articleSummaryExtractorsDialog = ArticleSummaryExtractorsDialog(this)
        articleSummaryExtractorsDialog.showDialog()
    }

    override fun onBackPressed() {
        val drawer = findViewById(R.id.drawer_layout) as DrawerLayout
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        when (item.itemId) {
//            R.id.action_settings -> return true
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when (item.itemId) {
            R.id.nav_camera -> {
                // Handle the camera action
            }
            R.id.nav_gallery -> {

            }
            R.id.nav_slideshow -> {

            }
            R.id.nav_manage -> {

            }
            R.id.nav_share -> {

            }
            R.id.nav_send -> {

            }
        }

        val drawer = findViewById(R.id.drawer_layout) as DrawerLayout
        drawer.closeDrawer(GravityCompat.START)
        return true
    }


    private fun clickedOnEntry(entry: Entry) {
        router.showEntryView(entry)
    }


    private fun setupDataAsync() {
        thread {
            AppComponent.component.inject(this)

            eventBus.register(eventBusListener)

            searchEngine.addInitializationListener {
                searchEngineInitialized()
            }
        }
    }

    private fun searchEngineInitialized() {
        retrieveAndShowEntries()
    }

    private fun retrieveAndShowEntries() {
        searchEngine.searchEntries(EntriesSearch { result ->
            runOnUiThread {
                entryAdapter.setItems(result)
            }
        })
    }


    inner class EventBusListener {

        @Handler()
        fun entriesChanged(entitiesOfTypeChanged: EntitiesOfTypeChanged) {
            retrieveAndShowEntries()
        }

    }

}