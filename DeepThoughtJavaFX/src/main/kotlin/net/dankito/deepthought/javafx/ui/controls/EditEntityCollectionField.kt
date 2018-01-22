package net.dankito.deepthought.javafx.ui.controls

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.geometry.Bounds
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Control
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.control.TextField
import javafx.scene.input.KeyCode
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.text.TextAlignment
import net.dankito.deepthought.javafx.util.FXUtils
import net.dankito.deepthought.model.BaseEntity
import tornadofx.*
import java.util.*


abstract class EditEntityCollectionField<T : BaseEntity> : View() {

    companion object {
        private const val LabelMarginRight = 6.0
    }


    lateinit var editedCollection: MutableCollection<T>
        private set

    val didCollectionChange = SimpleBooleanProperty()

    protected lateinit var originalCollection: Collection<T>


    protected val enteredSearchTerm = SimpleStringProperty("")

    protected val searchResults: ObservableList<T> = FXCollections.observableArrayList()

    private val showSearchResults = SimpleBooleanProperty()


    private var collectionLabel: Label by singleAssign()

    protected var txtfldEnteredSearchTerm: TextField by singleAssign()

    protected var collectionPreviewPane: Pane by singleAssign()

    private var countCollectionPreviewPaneLines = 0

    private var lstvwSearchResults: ListView<T> by singleAssign()



    abstract protected fun getLabelText(): String

    protected open fun getSearchFieldPromptText(): String {
        return ""
    }

//    abstract protected fun getSearchResultListCellFragment(): KClass<ListCellFragment<T>>

    abstract protected fun searchEntities(searchTerm: String)

    protected open fun enterPressed() {

    }

    abstract protected fun updateEditedCollectionPreviewOnUiThread()


    override val root = vbox {

        FXUtils.setBackgroundToColor(this, Color.WHITE)

        hbox {
            alignment = Pos.TOP_LEFT
            prefWidthProperty().bind(this@vbox.widthProperty())

            collectionLabel = label(getLabelText()) {
                minWidth = Control.USE_PREF_SIZE
                useMaxWidth = true

                prefHeight = 28.0 // TagView.height = 26.0 + margin top = 2.0
                textAlignment = TextAlignment.CENTER

                hboxConstraints {
                    alignment = Pos.CENTER_LEFT

                    marginRight = LabelMarginRight
                }
            }

            collectionPreviewPane = flowpane {
                minHeight = 20.0
                isFillWidth = true
                hgrow = Priority.ALWAYS

                vgap = 6.0
                hgap = 4.0

                hboxConstraints {
                    marginTop = 2.0
                }
            }

            minHeightProperty().bind(collectionPreviewPane.minHeightProperty())
            maxHeightProperty().bind(collectionPreviewPane.maxHeightProperty())

            vboxConstraints {
                marginBottom = 6.0
            }
        }

        txtfldEnteredSearchTerm = textfield(enteredSearchTerm) {
            minHeight = 26.0
            hgrow = Priority.ALWAYS

            promptText = getSearchFieldPromptText()

            textProperty().addListener { _, _, newValue -> searchEntities(newValue) }
            focusedProperty().addListener { _, _, newValue ->
                searchFieldOrSearchResultsFocusedChanged(newValue, lstvwSearchResults.isFocused)

                if(newValue) searchEntities(text)
            }

            setOnKeyReleased { event ->
                if(event.code == KeyCode.ENTER) {
                    enterPressed()
                }
            }

            vboxConstraints {
                marginTop = 6.0
            }

            // so that text field has same indentation as TagViews; cannot add text field to hbox above as then binding height wouldn't work anymore
            collectionLabel.widthProperty().addListener { _, _, newValue -> VBox.setMargin(this@textfield, Insets(6.0, 0.0, 0.0, newValue.toDouble() + LabelMarginRight)) }
        }

        lstvwSearchResults = listview(searchResults) {
            vgrow = Priority.ALWAYS
            minHeight = 100.0
            maxHeight = 200.0 // set maxHeight so that RichTextEditor doesn't get displayed over buttons bar

            visibleProperty().bind(showSearchResults)
            FXUtils.ensureNodeOnlyUsesSpaceIfVisible(this)

//            cellFragment(getSearchResultListCellFragment())

            focusedProperty().addListener { _, _, newValue -> searchFieldOrSearchResultsFocusedChanged(txtfldEnteredSearchTerm.isFocused, newValue) }
            onDoubleClick { toggleItemOnUiThread(selectionModel.selectedItem) }

//            contextmenu {
//                item(messages["action.edit"]) {
//                    action {
//                        selectedItem?.let { referenceListPresenter.editReference(it) }
//                    }
//                }
//
//                separator()
//
//                item(messages["action.delete"]) {
//                    action {
//                        selectedItem?.let { referenceListPresenter.deleteReference(it) }
//                    }
//                }
//            }

            vboxConstraints {
                marginTop = 6.0
            }
        }

        doCustomInitialization()
    }

    protected fun doCustomInitialization() {
        val boundsListener = { _: ObservableValue<out Bounds>, _: Bounds, _: Bounds -> checkIfFlowPaneShouldResize() }

        collectionPreviewPane.children.addListener(ListChangeListener<Node> { change ->
            while(change.next()) {
                if(change.wasAdded()) {
                    change.addedSubList.forEach { node -> node.boundsInParentProperty().addListener(boundsListener) }
                }

                if(change.wasRemoved()) {
                    change.removed.forEach { node -> node.boundsInParentProperty().removeListener(boundsListener) }
                }
            }

            checkIfFlowPaneShouldResize()
        })
    }

    /**
     * FlowPane doesn't update it's size automatically -> tell it to do so if necessary
     */
    private fun checkIfFlowPaneShouldResize() {
        val previousCountLines = this.countCollectionPreviewPaneLines
        this.countCollectionPreviewPaneLines = determineCountCollectionPreviewPaneLines()

        if(countCollectionPreviewPaneLines != previousCountLines) {
            updateFlowPaneHeight()
        }
    }

    private fun determineCountCollectionPreviewPaneLines(): Int {
        val childrenYValues = collectionPreviewPane.children.map { it.boundsInParent.minY }.toSet() // get all unique children minY values

        return childrenYValues.filter { it >= 0 }.size // filter out negative values; these occur when FlowPane isn't fully initialized yet
    }

    private fun updateFlowPaneHeight() {
        val newHeight = collectionPreviewPane.prefHeight(collectionPreviewPane.width)

        if(newHeight > 0.0) {
            runLater { // this method is called during layout process -> so wait some time before telling FlowPane's parent to change its size
                collectionPreviewPane.minHeight = newHeight
                collectionPreviewPane.maxHeight = newHeight
            }
        }
        else { // FlowPane not initialized yet
            countCollectionPreviewPaneLines = 0 // reset count lines therefore so that on next change updateFlowPaneHeight() gets called again
        }
    }


    fun setCollectionToEdit(originalCollection: MutableCollection<T>) {
        this.originalCollection = originalCollection

        editedCollection = LinkedHashSet(originalCollection) // make a copy so that original collection doesn't get manipulated
        updateEditedCollectionPreview()
    }

    private fun toggleItemOnUiThread(searchResult: T?) {
        if(searchResult != null) {
            if(editedCollection.contains(searchResult)) {
                addItemToEditedCollection(searchResult)
            }
            else {
                removeItemFromEditedCollection(searchResult)
            }

            updateEditedCollectionPreviewOnUiThread()
            updateDidCollectionChange()
        }
    }

    protected open fun addItemToEditedCollection(item: T) {
        editedCollection.remove(item)
    }

    protected open fun removeItemFromEditedCollection(item: T) {
        editedCollection.add(item)
    }

    protected fun updateEditedCollectionPreview() {
        runLater { updateEditedCollectionPreviewOnUiThread() }
    }

    private fun setCollectionPreviewOnUIThread() {
        updateEditedCollectionPreviewOnUiThread()

        if(searchResults.isEmpty() || txtfldEnteredSearchTerm.isFocused == false) {
            hideSearchResultsView()
        }
        else {
            showSearchResultsView()
        }

        updateDidCollectionChange()
    }

    protected fun updateDidCollectionChange() {
        this.didCollectionChange.value = didCollectionChange()
    }

    protected open fun didCollectionChange(): Boolean {
        return didCollectionChange(originalCollection, editedCollection)
    }

    protected fun didCollectionChange(originalCollection: Collection<T>, editedCollection: Collection<T>): Boolean {
        if(originalCollection.size != editedCollection.size) {
            return true
        }

        val copy = java.util.ArrayList(editedCollection)
        copy.removeAll(originalCollection)
        return copy.size > 0
    }

    protected fun retrievedSearchResults(result: Collection<T>) {
        runLater { retrievedSearchResultsOnUiThread(result) }
    }

    protected fun retrievedSearchResultsOnUiThread(result: Collection<T>) {
        searchResults.setAll(result)

        if(txtfldEnteredSearchTerm.isFocused || lstvwSearchResults.isFocused) {
            showSearchResultsView()
        }
    }

    private fun searchFieldOrSearchResultsFocusedChanged(titleHasFocus: Boolean, searchResultsHaveFocus: Boolean) {
        showSearchResults.value = titleHasFocus || searchResultsHaveFocus
    }

    protected fun showSearchResultsView() {
        showSearchResults.value = true
    }

    protected fun hideSearchResultsView() {
        showSearchResults.value = false
    }

}