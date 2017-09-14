package net.dankito.deepthought.android.adapter

import android.graphics.Typeface
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.daimajia.swipe.SwipeLayout
import net.dankito.deepthought.android.R
import net.dankito.deepthought.android.adapter.viewholder.TagViewHolder
import net.dankito.deepthought.model.CalculatedTag
import net.dankito.deepthought.model.Tag
import net.dankito.deepthought.ui.presenter.TagsListPresenter
import net.dankito.deepthought.ui.tags.TagSearchResultState


class TagRecyclerAdapter(private val presenter: TagsListPresenter): MultiSelectListRecyclerSwipeAdapter<Tag, TagViewHolder>() {

    override fun getSwipeLayoutResourceId(position: Int) = R.id.tagSwipeLayout


    override fun itemLongClicked(viewHolder: RecyclerView.ViewHolder, item: Tag, position: Int) {
        if(item is CalculatedTag == false) { // avoid that a CalculatedTag gets selected
            super.itemLongClicked(viewHolder, item, position)
        }
    }

    override fun itemClicked(viewHolder: RecyclerView.ViewHolder, item: Tag, position: Int): Boolean {
        if(item is CalculatedTag == false) {
            return super.itemClicked(viewHolder, item, position)
        }
        else {
            notifyItemChanged(position) // so that onBindViewHolder() gets called and isActivated flag removed from view
            return true
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): TagViewHolder {
        val itemView = LayoutInflater.from(parent?.context).inflate(R.layout.list_item_tag, parent, false)

        val viewHolder = TagViewHolder(itemView)

        viewHolderCreated(viewHolder)
        return viewHolder
    }

    override fun bindViewForNullValue(viewHolder: TagViewHolder) {
        super.bindViewForNullValue(viewHolder)

        setBackgroundForDefaultState(viewHolder.itemView)
    }

    override fun bindItemToView(viewHolder: TagViewHolder, item: Tag) {
        if(presenter.isTagFilterApplied()) {
            viewHolder.txtTagDisplayText.text = "${item.name} (${presenter.getCountEntriesForFilteredTag(item)} / ${item.countEntries})"
        }
        else {
            viewHolder.txtTagDisplayText.text = item.displayText
        }

        setFilterIconDependingOnTagState(item, viewHolder.imgFilter)
        viewHolder.imgFilter.setOnClickListener { presenter.toggleFilterTag(item) }

        setBackgroundColor(viewHolder.itemView, item)
        setTextColor(viewHolder.txtTagDisplayText, item)

        if(item is CalculatedTag) { // make CalculatedTags unselectable in multi select mode
            viewHolder.itemView.isActivated = false
        }
    }

    override fun setupSwipeView(viewHolder: TagViewHolder, item: Tag) {
        viewHolder.btnEditTag.setOnClickListener {
            presenter.editTag(item)
            closeSwipeView(viewHolder)
        }

        viewHolder.btnDeleteTag.setOnClickListener {
            presenter.deleteTagAsync(item)
            closeSwipeView(viewHolder)
        }

        (viewHolder.itemView as? SwipeLayout)?.isSwipeEnabled = item is CalculatedTag == false
    }


    private fun setFilterIconDependingOnTagState(tag: Tag, imgFilter: ImageView) {
        if(tag is CalculatedTag) {
            imgFilter.visibility = View.INVISIBLE
        }
        else {
            imgFilter.visibility = View.VISIBLE

            when(presenter.isTagFiltered(tag)) {
                true -> imgFilter.setImageResource(R.drawable.filter)
                false -> imgFilter.setImageResource(R.drawable.filter_disabled)
            }
        }
    }

    private fun setTextColor(txtTagDisplayText: TextView, tag: Tag) {
        if(presenter.isTagFiltered(tag)) {
            txtTagDisplayText.setTextColor(txtTagDisplayText.context.resources.getColor(R.color.colorAccent))
            txtTagDisplayText.setTypeface(null, Typeface.BOLD)
        }
        else {
            txtTagDisplayText.setTextColor(txtTagDisplayText.context.resources.getColor(R.color.unselected_item_text_color))
            txtTagDisplayText.setTypeface(null, Typeface.NORMAL)
        }
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

    private fun getDefaultBackgroundColor() = R.drawable.list_item_background

}