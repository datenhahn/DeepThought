package net.dankito.deepthought.android.views

import android.app.Activity
import android.view.LayoutInflater
import com.github.clans.fab.FloatingActionButton
import com.github.clans.fab.FloatingActionMenu
import kotlinx.android.synthetic.main.view_floating_action_button_main.view.*
import net.dankito.deepthought.android.R
import net.dankito.deepthought.model.ArticleSummaryExtractorConfig
import net.dankito.deepthought.news.summary.config.ArticleSummaryExtractorConfigManager
import net.dankito.deepthought.ui.IRouter
import net.dankito.service.data.messages.ArticleSummaryExtractorConfigChanged
import net.dankito.service.eventbus.IEventBus
import net.engio.mbassy.listener.Handler


class MainActivityFloatingActionMenuButton(floatingActionMenu: FloatingActionMenu, private val summaryExtractorsManager: ArticleSummaryExtractorConfigManager, private val router: IRouter,
                               private val eventBus: IEventBus) : FloatingActionMenuButton(floatingActionMenu) {


    private val favoriteArticleSummaryExtractorsButtons = ArrayList<FloatingActionButton>()

    private val eventBusListener = EventBusListener()


    init {
        setup()

        summaryExtractorsManager.addInitializationListener { setFavoriteArticleSummaryExtractors() }
    }


    private fun setup() {
        floatingActionMenu.fabCreateItem.setOnClickListener { executeAndCloseMenu { router.showCreateItemView() } }
        floatingActionMenu.fabCreateItemFromPdf.setOnClickListener { executeAndCloseMenu { router.createItemFromPdf() } }
        floatingActionMenu.fabAddNewspaperArticle.setOnClickListener { executeAndCloseMenu { router.showArticleSummaryExtractorsView() } }

        setFavoriteArticleSummaryExtractors()
    }


    fun viewBecomesVisible() {
        eventBus.register(eventBusListener)

        setFavoriteArticleSummaryExtractors() // as may in the meantime favorite article summary extractors changed
    }

    fun viewGetsHidden() {
        eventBus.unregister(eventBusListener) // floatingActionMenu.addOnAttachStateChangeListener() didn't work, caused a memory leak, calling it explicitly now from MainActivity
    }


    private fun setFavoriteArticleSummaryExtractors() {
        val activity = floatingActionMenu.context as Activity

        activity.runOnUiThread { setFavoriteArticleSummaryExtractorsOnUIThread(activity, summaryExtractorsManager.getFavorites()) }
    }

    private fun setFavoriteArticleSummaryExtractorsOnUIThread(activity: Activity, favoriteArticleSummaryExtractors: List<ArticleSummaryExtractorConfig>) {
        clearFavoriteArticleSummaryExtractorsButtons()

        if(favoriteArticleSummaryExtractors.isNotEmpty()) {
            val layoutInflater = activity.layoutInflater

            favoriteArticleSummaryExtractors.forEach { extractorConfig ->
                addFavoriteArticleSummaryExtractorsButton(layoutInflater, activity, extractorConfig)
            }
        }
    }

    private fun addFavoriteArticleSummaryExtractorsButton(layoutInflater: LayoutInflater, activity: Activity, extractorConfig: ArticleSummaryExtractorConfig) {
        val menuButton = layoutInflater.inflate(R.layout.view_floating_action_menu_button, null) as FloatingActionButton
        menuButton.labelText = activity.getString(R.string.floating_action_button_add_article_of_newspaper, extractorConfig.name)

        menuButton.setOnClickListener { executeAndCloseMenu { router.showArticleSummaryView(extractorConfig) } }

        floatingActionMenu.addMenuButton(menuButton, 0)

        favoriteArticleSummaryExtractorsButtons.add(menuButton)
    }

    private fun clearFavoriteArticleSummaryExtractorsButtons() {
        favoriteArticleSummaryExtractorsButtons.forEach { floatingActionMenu.removeMenuButton(it) }
        favoriteArticleSummaryExtractorsButtons.clear()
    }


    inner class EventBusListener {

        @Handler
        fun articleSummaryExtractorsChanged(changed: ArticleSummaryExtractorConfigChanged) {
            setFavoriteArticleSummaryExtractors()
        }
    }

}