package net.dankito.deepthought.android.fragments

import android.app.Activity
import android.support.v7.widget.RecyclerView
import android.view.ActionMode
import android.view.MenuItem
import net.dankito.deepthought.android.R
import net.dankito.deepthought.android.adapter.MultiSelectListRecyclerSwipeAdapter
import net.dankito.deepthought.android.adapter.ReferenceRecyclerAdapter
import net.dankito.deepthought.android.di.AppComponent
import net.dankito.deepthought.android.dialogs.ReferenceEntriesListDialog
import net.dankito.deepthought.model.Source
import net.dankito.deepthought.ui.IRouter
import net.dankito.deepthought.ui.presenter.IMainViewSectionPresenter
import net.dankito.deepthought.ui.presenter.ReferencesListPresenter
import net.dankito.deepthought.ui.view.IReferencesListView
import net.dankito.service.data.DeleteEntityService
import net.dankito.service.data.ReferenceService
import net.dankito.utils.ui.IClipboardService
import javax.inject.Inject


class ReferencesListView: EntitiesListViewFragment<Source>(R.menu.reference_contextual_action_menu, R.string.tab_source_onboarding_text),
        IReferencesListView {

    @Inject
    protected lateinit var referenceService: ReferenceService

    @Inject
    protected lateinit var router: IRouter

    @Inject
    protected lateinit var clipboardService: IClipboardService

    @Inject
    protected lateinit var deleteEntityService: DeleteEntityService


    private val presenter: ReferencesListPresenter

    private val adapter: ReferenceRecyclerAdapter


    init {
        AppComponent.component.inject(this)

        presenter = ReferencesListPresenter(this, searchEngine, router, clipboardService, deleteEntityService)

        adapter = ReferenceRecyclerAdapter(presenter)
    }


    override fun initPresenter(): IMainViewSectionPresenter {
        return presenter
    }

    override fun getListAdapter(): MultiSelectListRecyclerSwipeAdapter<Source, out RecyclerView.ViewHolder> {
        return adapter
    }

    override fun listItemClicked(selectedItem: Source) {
        hideSearchViewKeyboard()
        presenter.showItemsForSource(selectedItem)
    }

    override fun actionItemSelected(mode: ActionMode, actionItem: MenuItem, selectedItems: Set<Source>): Boolean {
        when(actionItem.itemId) {
            R.id.mnEditReference -> {
                selectedItems.forEach { presenter.editReference(it) }
                mode.finish()
                return true
            }
            R.id.mnDeleteReference -> {
                selectedItems.forEach { presenter.deleteReference(it) }
                mode.finish()
                return true
            }
            else -> return false
        }
    }


    override fun getQueryHint(activity: Activity) = activity.getString(R.string.search_hint_sources)

    override fun searchEntities(query: String) {
        presenter.searchReferences(query)
    }


    override fun onBackPressed(): Boolean {
        if(isReferenceEntriesListDialogVisible()) { // let ReferenceEntriesListDialog handle back button press
            return false
        }

        return super.onBackPressed()
    }

    private fun isReferenceEntriesListDialogVisible(): Boolean {
        return fragmentManager.findFragmentByTag(ReferenceEntriesListDialog.TAG) != null
    }


    /*      IReferencesListView implementation      */

    override fun showEntities(entities: List<Source>) {
        activity?.runOnUiThread {
            adapter.items = entities

            retrievedEntitiesOnUiThread(entities)
        }
    }

}