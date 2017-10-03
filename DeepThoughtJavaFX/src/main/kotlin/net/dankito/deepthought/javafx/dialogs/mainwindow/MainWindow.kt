package net.dankito.deepthought.javafx.dialogs.mainwindow

import javafx.scene.control.SplitPane
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import javafx.scene.image.Image
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import net.dankito.deepthought.javafx.di.AppComponent
import net.dankito.deepthought.javafx.dialogs.mainwindow.controls.EntriesListView
import net.dankito.deepthought.javafx.dialogs.mainwindow.controls.MainMenuBar
import net.dankito.deepthought.javafx.dialogs.mainwindow.controls.TagsListView
import tornadofx.*


class MainWindow : View() {

    override val root: BorderPane by fxml()

    private var tbpnOverview: TabPane by singleAssign()

    private var tabTags: Tab by singleAssign()

    private var splpnContent: SplitPane by singleAssign()

    private var contentPane: VBox by singleAssign()

    val mainMenuBar: MainMenuBar by inject()

    val tagsListView: TagsListView by inject()

    val entriesListView: EntriesListView by inject()


    init {
        AppComponent.component.inject(this)

        setupUI()
    }

    private fun setupUI() {
        title = messages["main.window.title"] // TODO: set icon
        setStageIcon(Image(MainWindow::class.java.classLoader.getResourceAsStream("icons/AppIcon.png")))

        splpnContent = splitpane {
            tbpnOverview = tabpane {
                prefWidth = 300.0
                tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE

                tabTags = tab(messages["tags.tab.label"]) {
                    prefWidth = 300.0

                    add(tagsListView.root)
                }
            }

            contentPane = vbox {

            }
        }

        root.center = splpnContent

        contentPane.add(entriesListView.root)
        VBox.setVgrow(entriesListView.root, Priority.ALWAYS)

        splpnContent.setDividerPosition(0, 0.2)

        root.top = mainMenuBar.root
    }

}