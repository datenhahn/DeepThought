package net.dankito.deepthought.javafx.ui.controls

import javafx.beans.value.ObservableValue
import javafx.event.EventTarget
import javafx.scene.control.TextField
import tornadofx.*


fun <T> EventTarget.autocompletionsearchtextfield(value: String? = null, op: AutoCompletionSearchTextField<T>.() -> Unit = {}) =
        opcr(this, AutoCompletionSearchTextField<T>().apply { if (value != null) text = value }, op)

fun <T> EventTarget.autocompletionsearchtextfield(property: ObservableValue<String>, op: AutoCompletionSearchTextField<T>.() -> Unit = {}): AutoCompletionSearchTextField<T>
        = autocompletionsearchtextfield<T>().apply {
    bind(property)
    op(this)
}



class AutoCompletionSearchTextField<T> : TextField() {

    var onAutoCompletion: ((T) -> Unit)? = null

    private var autoCompletionBinding = AutoCompletionBinding<T>(this)


    init {
        autoCompletionBinding.setOnAutoCompleted { e -> onAutoCompletion?.invoke(e.completion) }
    }


    fun setAutoCompleteList(autoCompletionList: Collection<T>, queryToSelectFromAutoCompletionList: String = text) {
        autoCompletionBinding.setAutoCompleteList(autoCompletionList, queryToSelectFromAutoCompletionList)
    }

}