package net.dankito.deepthought.javafx.dialogs.mainwindow

import javafx.scene.control.SplitPane
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import javafx.scene.image.Image
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import net.dankito.deepthought.javafx.di.AppComponent
import net.dankito.deepthought.javafx.dialogs.mainwindow.controls.EntriesListView
import net.dankito.deepthought.javafx.dialogs.mainwindow.controls.MainMenuBar
import net.dankito.deepthought.javafx.dialogs.mainwindow.controls.StatusBar
import net.dankito.deepthought.javafx.dialogs.mainwindow.controls.TagsListView
import tornadofx.*
import tornadofx.FX.Companion.messages


class MainWindow : View(messages["main.window.title"]) {

    private var tbpnOverview: TabPane by singleAssign()

    private var tabTags: Tab by singleAssign()

    private var splpnContent: SplitPane by singleAssign()

    private var contentPane: VBox by singleAssign()

    val mainMenuBar: MainMenuBar by inject()

    val tagsListView: TagsListView by inject()

    val entriesListView: EntriesListView by inject()

    val statusBar: StatusBar by inject()


    init {
        AppComponent.component.inject(this)

        setupUI()
    }



    override val root = borderpane {
        prefHeight = 620.0
        prefWidth = 1150.0

        top = mainMenuBar.root

        center {
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

            contentPane.add(entriesListView.root)
            VBox.setVgrow(entriesListView.root, Priority.ALWAYS)
            entriesListView.statusBar = statusBar

            splpnContent.setDividerPosition(0, 0.2)
        }

        bottom = statusBar.root
    }


    private fun setupUI() {
        setStageIcon(Image(MainWindow::class.java.classLoader.getResourceAsStream("icons/AppIcon.png")))
    }

}