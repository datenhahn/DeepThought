package net.dankito.deepthought.android.service

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.support.v4.content.ContextCompat
import android.text.Html


class HtmlLocalResourceImageGetter(private val context: Context, private val tintColor: Int? = null) : Html.ImageGetter {

    override fun getDrawable(source: String?): Drawable? {
        source?.let {
            val resourceId = context.resources.getIdentifier(source , "drawable", context.packageName)
            if(resourceId > 0) { // 0 == resource not found
                val drawable = ContextCompat.getDrawable(context, resourceId)
                val size = (16 * context.resources.displayMetrics.density).toInt() // TODO: this is bad applying a fixed size of 16
                drawable.setBounds(0, 0, size, size) // thanks to Pratik Sharma for pointing that out to me, otherwise it won't get displayed (https://stackoverflow.com/a/14379821/8837882)

                tintColor?.let {
                    drawable.setColorFilter(tintColor, PorterDuff.Mode.SRC_ATOP)
                }

                return drawable
            }
        }

        return null
    }

}