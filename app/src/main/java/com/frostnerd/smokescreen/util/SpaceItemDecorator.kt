package com.frostnerd.smokescreen.util

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class SpaceItemDecorator(context: Context, spaceDp: Int = 12) : RecyclerView.ItemDecoration() {
    private val decorationHeight: Int =
        spaceDp * (context.resources.displayMetrics.densityDpi.toFloat() / 160.0f).toInt()

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        super.getItemOffsets(outRect, view, parent, state)

        val itemPosition = parent.getChildAdapterPosition(view)
        val totalCount = parent.adapter!!.itemCount

        if (itemPosition >= 0 && itemPosition < totalCount - 1) {
            outRect.bottom = decorationHeight
        }
    }
}