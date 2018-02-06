package net.dankito.deepthought.android.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.list_item_reference.view.*
import net.dankito.deepthought.android.R
import net.dankito.deepthought.android.adapter.viewholder.ReferenceViewHolder
import net.dankito.deepthought.model.Source
import net.dankito.deepthought.model.extensions.preview
import net.dankito.deepthought.model.extensions.seriesAndPublishingDatePreview
import net.dankito.deepthought.ui.presenter.SourcePresenterBase


abstract class ReferenceRecyclerAdapterBase(private val presenter: SourcePresenterBase): MultiSelectListRecyclerSwipeAdapter<Source, ReferenceViewHolder>() {

    abstract val shouldShowImageIsSourceAddedToItem: Boolean

    abstract val shouldShowChevronRight: Boolean

    abstract val shouldShowButtonEditSource: Boolean

    protected open fun isAddedToEntity(source: Source): Boolean {
        return false
    }


    override fun getSwipeLayoutResourceId(position: Int) = R.id.referenceSwipeLayout


    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ReferenceViewHolder {
        val itemView = LayoutInflater.from(parent?.context).inflate(R.layout.list_item_reference, parent, false)

        itemView.vwIsReferenceSetOnEntity.isShowAddedViewEnabled = shouldShowImageIsSourceAddedToItem
        itemView.vwIsReferenceSetOnEntity.setEntityNameTextSizeToHeader1TextSize()

        val viewHolder = ReferenceViewHolder(itemView)

        viewHolderCreated(viewHolder)
        return viewHolder
    }

    override fun bindViewForNullValue(viewHolder: ReferenceViewHolder) {
        super.bindViewForNullValue(viewHolder)

        viewHolder.vwIsReferenceSetOnEntity.showState("", false)

        viewHolder.imgChevronRight.visibility = View.GONE
    }

    override fun bindItemToView(viewHolder: ReferenceViewHolder, item: Source) {
        var seriesPreview: String? = item.seriesAndPublishingDatePreview
        if(seriesPreview.isNullOrBlank()) seriesPreview = null

        val isAddedToEntity = shouldShowImageIsSourceAddedToItem && isAddedToEntity(item)

        viewHolder.vwIsReferenceSetOnEntity.showState(item.preview, isAddedToEntity, seriesPreview)

        viewHolder.imgChevronRight.visibility = if(shouldShowChevronRight) View.VISIBLE else View.GONE
    }

    override fun setupSwipeView(viewHolder: ReferenceViewHolder, item: Source) {
        viewHolder.btnEditReference.visibility = if(shouldShowButtonEditSource) View.VISIBLE else View.GONE
        viewHolder.btnShareReference.visibility = if(item.url.isNullOrBlank()) View.GONE else View.VISIBLE

        viewHolder.btnEditReference.setOnClickListener {
            presenter.editSource(item)
            closeSwipeView(viewHolder)
        }

        viewHolder.btnShareReference.setOnClickListener {
            presenter.copySourceUrlToClipboard(item)
            closeSwipeView(viewHolder)
        }

        viewHolder.btnDeleteReference.setOnClickListener {
            presenter.deleteSource(item)
            closeSwipeView(viewHolder)
        }
    }

}