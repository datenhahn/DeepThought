package net.dankito.deepthought.javafx.dialogs.mainwindow.controls

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.control.ContextMenu
import javafx.scene.control.TableView
import javafx.scene.layout.Priority
import net.dankito.deepthought.javafx.di.AppComponent
import net.dankito.deepthought.javafx.dialogs.mainwindow.model.TagViewModel
import net.dankito.deepthought.javafx.service.extensions.findClickedTableRow
import net.dankito.deepthought.model.AllCalculatedTags
import net.dankito.deepthought.model.CalculatedTag
import net.dankito.deepthought.model.Tag
import net.dankito.deepthought.ui.IRouter
import net.dankito.deepthought.ui.presenter.TagsListPresenter
import net.dankito.deepthought.ui.tags.TagsSearchResultsUtil
import net.dankito.deepthought.ui.view.ITagsListView
import net.dankito.service.data.DeleteEntityService
import net.dankito.service.data.TagService
import net.dankito.service.search.ISearchEngine
import net.dankito.service.search.Search
import net.dankito.utils.ui.dialogs.IDialogService
import tornadofx.*
import javax.inject.Inject


class TagsListView : EntitiesListView(), ITagsListView {

    private val presenter: TagsListPresenter

    private val searchBar: TagsSearchBar

    private lateinit var tableTags: TableView<Tag>


    private val tags: ObservableList<Tag> = FXCollections.observableArrayList()

    private val tagViewModel = TagViewModel()

    private var lastSelectedTag: Tag? = null


    @Inject
    protected lateinit var searchEngine: ISearchEngine

    @Inject
    protected lateinit var searchResultsUtil: TagsSearchResultsUtil

    @Inject
    protected lateinit var tagService: TagService

    @Inject
    protected lateinit var deleteEntityService: DeleteEntityService

    @Inject
    protected lateinit var dialogService: IDialogService

    @Inject
    protected lateinit var router: IRouter

    @Inject
    protected lateinit var allCalculatedTags: AllCalculatedTags


    init {
        AppComponent.component.inject(this)

        presenter = TagsListPresenter(this, allCalculatedTags, searchEngine, searchResultsUtil, tagService, deleteEntityService, dialogService, router)
        searchBar = TagsSearchBar(this)

        runLater { // wait till UI is initialized before search index
            searchEngine.addInitializationListener {
                searchEntities(Search.EmptySearchTerm)
            }
        }
    }


    override fun onDock() {
        super.onDock()

        presenter.viewBecomesVisible()
    }

    override fun onUndock() {
        presenter.viewGetsHidden()

        super.onUndock()
    }


    override val root = vbox {
        add(searchBar.root)

        tableTags = tableview<Tag> {
            column(messages["tag.column.header.name"], Tag::displayText) {
                isResizable = true
                makeEditable()
                setSortable(false)
                tableViewProperty().addListener { _, _, newValue ->
                    if(newValue != null) {
                        prefWidthProperty().bind(newValue.widthProperty().subtract(2)) // subtract(2): otherwise a useless scrollbar would be displayed
                    }
                }
            }

            // TODO: add filter column
//            column("") {
//                isResizable = false
//                minWidth = 35.0
//                maxWidth = 35.0
//            }

            columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY

            items = tags

            bindSelected(tagViewModel)

            vgrow = Priority.ALWAYS

            // don't know why but selectionModel.selectedItemProperty() doesn't work reliably. Another tag gets selected but selectedItemProperty() doesn't fire this change
            selectionModel.selectedIndexProperty().addListener { _, _, newValue -> tagSelected(newValue.toInt()) }

            var currentMenu: ContextMenu? = null
            setOnContextMenuRequested { event ->
                currentMenu?.hide()

                val tableRow = event.pickResult?.findClickedTableRow<Tag>()
                tableRow?.item?.let { clickedItem ->
                    currentMenu = createContextMenuForItem(clickedItem)
                    currentMenu?.show(this, event.screenX, event.screenY)
                }
            }
        }
    }

    private fun createContextMenuForItem(clickedTag: Tag): ContextMenu? {
        if(clickedTag is CalculatedTag) {
            return null
        }

        val contextMenu = ContextMenu()

        contextMenu.item(messages["action.edit"]) {
            action {
                presenter.editTag(clickedTag)
            }
        }

        separator()

        contextMenu.item(messages["action.delete"]) {
            action {
                presenter.deleteTagAsync(clickedTag)
            }
        }

        return contextMenu
    }


    fun viewCameIntoView() {
        showItemsForLastSelectedEntity()
    }

    private fun tagSelected(selectedTagIndex: Int) {
        if(selectedTagIndex >= 0 && selectedTagIndex < tags.size) {
            tagSelected(tags[selectedTagIndex])
        }
    }

    private fun tagSelected(selectedTag: Tag?) {
        if(selectedTag != lastSelectedTag) { // to avoid that Lucene is unnecessarily queried for the same Tag again
            if(selectedTag != null) {
                lastSelectedTag = selectedTag
            }
            else {
//            presenter.clearSelectedTag() // TODO
            }

            showItemsForLastSelectedEntity()
        }
    }

    private fun showItemsForLastSelectedEntity() {
        val selectedTag = lastSelectedTag

        if(selectedTag != null) {
            presenter.showItemsForTag(selectedTag)
        }
        else if(tags.isNotEmpty()) {
            tableTags.selectionModel.select(0) // this will select first tag in list and then show its items
        }
    }


    override fun searchEntities(query: String) {
        presenter.searchTags(query)
    }


    /*          ITagsListView implementation            */

    override fun showEntities(entities: List<Tag>) {
        runLater {
            this.tags.setAll(entities)

            if(tableTags.selectedItem == null && entities.isNotEmpty()) { // on start up no tag is selected -> select first from list
                tableTags.selectionModel.select(0)
            }
        }
    }

    override fun updateDisplayedTags() {
        runLater { tableTags.items.invalidate() }
    }

}