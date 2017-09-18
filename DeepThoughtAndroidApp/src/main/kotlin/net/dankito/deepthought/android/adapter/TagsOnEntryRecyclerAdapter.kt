package net.dankito.deepthought.android.adapter

import android.graphics.Typeface
import android.os.Build
import android.support.v4.graphics.drawable.DrawableCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.daimajia.swipe.SwipeLayout
import kotlinx.android.synthetic.main.list_item_tag_on_entry.view.*
import net.dankito.deepthought.android.R
import net.dankito.deepthought.android.adapter.viewholder.TagsOnEntryViewHolder
import net.dankito.deepthought.model.Tag
import net.dankito.deepthought.ui.presenter.TagsOnEntryListPresenter
import net.dankito.deepthought.ui.tags.TagSearchResultState
import java.util.*


class TagsOnEntryRecyclerAdapter(private val presenter: TagsOnEntryListPresenter, val listener: (MutableList<Tag>) -> Unit) : ListRecyclerSwipeAdapter<Tag, TagsOnEntryViewHolder>() {

    var tagsOnEntry: MutableList<Tag> = mutableListOf()

    var deleteTagListener: ((Tag) -> Unit)? = null


    override fun getSwipeLayoutResourceId(position: Int) = R.id.tagOnEntrySwipeLayout


    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): TagsOnEntryViewHolder {
        val itemView = LayoutInflater.from(parent?.context).inflate(R.layout.list_item_tag_on_entry, parent, false)

        setIconTintList(itemView)

        val viewHolder = TagsOnEntryViewHolder(itemView)

        (itemView as? SwipeLayout)?.addRevealListener(itemView.id) { _, _, _, _ -> viewHolder.lastItemSwipeTime = Date() }

        viewHolderCreated(viewHolder)
        return viewHolder
    }

    private fun setIconTintList(itemView: View) {
        val imgIsTagAddedToEntry = itemView.imgIsTagAddedToEntry
        val resources = itemView.context.resources

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            imgIsTagAddedToEntry.imageTintList = resources.getColorStateList(R.color.is_entity_selected_icon_color, itemView.context.theme)
        }
        else {
            DrawableCompat.setTintList(itemView.imgIsTagAddedToEntry.drawable, resources.getColorStateList(R.color.is_entity_selected_icon_color))
        }
    }


    override fun bindViewForNullValue(viewHolder: TagsOnEntryViewHolder) {
        super.bindViewForNullValue(viewHolder)

        setBackgroundForDefaultState(viewHolder.itemView)
    }

    override fun bindItemToView(viewHolder: TagsOnEntryViewHolder, item: Tag) {
        val isAddedToEntry = tagsOnEntry.contains(item)

        viewHolder.txtvwTagName.text = item.displayText

        if(isAddedToEntry) {
            viewHolder.imgIsTagAddedToEntry.setImageResource(R.drawable.ic_checkmark)
            viewHolder.txtvwTagName.setTypeface(null, Typeface.BOLD)
        }
        else {
            viewHolder.imgIsTagAddedToEntry.setImageResource(R.drawable.ic_add)
            viewHolder.txtvwTagName.setTypeface(null, Typeface.NORMAL)
        }

        viewHolder.itemView.isActivated = isAddedToEntry // sets icon's tint and textview's text color

        setBackgroundColor(viewHolder.itemView, item)

        viewHolder.itemView.setOnClickListener { itemClicked(viewHolder, item) }
    }

    override fun setupSwipeView(viewHolder: TagsOnEntryViewHolder, item: Tag) {
        viewHolder.btnEditTag.setOnClickListener {
            presenter.editTag(item)
            closeSwipeView(viewHolder)
        }

        viewHolder.btnDeleteTag.setOnClickListener {
            deleteTagListener?.invoke(item)
            closeSwipeView(viewHolder)
        }
    }


    private fun itemClicked(viewHolder: TagsOnEntryViewHolder, tag: Tag) {
        val lastSwipeTime = viewHolder.lastItemSwipeTime

        // a swipe on an item also triggers onClickListener -> filter out swipes before calling toggleTagOnEntryOnUIThread() as otherwise swipe layout would get closed
        // immediately again -> wouldn't be possible to activate list item actions anymore
        if(lastSwipeTime == null || Date().time - lastSwipeTime.time > 200) {
            toggleTagOnEntryOnUIThread(tag)
        }
    }

    private fun toggleTagOnEntryOnUIThread(tag: Tag) {
        if(tagsOnEntry.contains(tag)) {
            tagsOnEntry.remove(tag)
        }
        else {
            tagsOnEntry.add(tag)
        }

        notifyDataSetChanged()

        callListener()
    }

    private fun callListener() {
        listener(tagsOnEntry)
    }


    private fun setBackgroundColor(view: View, tag: Tag) {
        val state = presenter.getTagSearchResultState(tag)

        view.setBackgroundResource(getColorForState(state))
    }

    private fun setBackgroundForDefaultState(view: View) {
        view.setBackgroundResource(getDefaultBackgroundColor())
    }

    private fun getColorForState(state: TagSearchResultState): Int {
        when(state) {
            TagSearchResultState.EXACT_OR_SINGLE_MATCH_BUT_NOT_OF_LAST_RESULT -> return R.color.tag_state_exact_or_single_match_but_not_of_last_result
            TagSearchResultState.MATCH_BUT_NOT_OF_LAST_RESULT -> return R.color.tag_state_match_but_not_of_last_result
            TagSearchResultState.EXACT_MATCH_OF_LAST_RESULT -> return R.color.tag_state_exact_match_of_last_result
            TagSearchResultState.SINGLE_MATCH_OF_LAST_RESULT -> return R.color.tag_state_single_match_of_last_result
            else -> return getDefaultBackgroundColor()
        }
    }

    private fun getDefaultBackgroundColor() = android.R.color.transparent

}