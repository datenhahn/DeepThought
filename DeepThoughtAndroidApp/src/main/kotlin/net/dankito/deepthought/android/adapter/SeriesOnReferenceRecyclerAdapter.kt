package net.dankito.deepthought.android.adapter

import android.os.Build
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import android.widget.ImageView
import net.dankito.deepthought.android.R
import net.dankito.deepthought.android.adapter.viewholder.SeriesViewHolder
import net.dankito.deepthought.model.Series
import net.dankito.deepthought.ui.presenter.SeriesPresenterBase


class SeriesOnReferenceRecyclerAdapter(presenter: SeriesPresenterBase): SeriesRecyclerAdapterBase(presenter) {

    var selectedSeries: Series? = null


    override val shouldShowImageIsSeriesSetOnReference: Boolean
        get() = true

    override val shouldShowChevronRight: Boolean
        get() = false

    override val shouldShowButtonEditSeries: Boolean
        get() = false


    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): SeriesViewHolder {
        val viewHolder = super.onCreateViewHolder(parent, viewType)

        setIconTintList(viewHolder.imgIsSeriesSetOnReference)

        return viewHolder
    }

    private fun setIconTintList(imgIsReferenceAddedToEntry: ImageView) {
        val context = imgIsReferenceAddedToEntry.context
        val resources = context.resources

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            imgIsReferenceAddedToEntry.imageTintList = resources.getColorStateList(R.color.is_entity_selected_icon_color, context.theme)
        }
        else {
            DrawableCompat.setTintList(imgIsReferenceAddedToEntry.drawable, resources.getColorStateList(R.color.is_entity_selected_icon_color))
        }
    }


    override fun itemBound(viewHolder: RecyclerView.ViewHolder, item: Series, position: Int) {
        super.itemBound(viewHolder, item, position)

        viewHolder.itemView.isActivated = selectedSeries == item
    }

}