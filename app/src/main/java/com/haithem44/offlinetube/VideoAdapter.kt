package com.haithem44.offlinetube

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.ImageLoader
import coil.load
import coil.request.videoFrameMillis
import java.util.Locale

/** صف في القائمة: الفيديو + موضع المشاهدة (ليتغير الصف ويُحدَّث شريط التقدم تلقائيًا عبر DiffUtil). */
data class VideoRow(val video: VideoItem, val position: Long)

private object VideoRowDiff : DiffUtil.ItemCallback<VideoRow>() {
    override fun areItemsTheSame(a: VideoRow, b: VideoRow) = a.video.id == b.video.id
    override fun areContentsTheSame(a: VideoRow, b: VideoRow) = a == b
}

class VideoAdapter(
    private val style: Style,
    private val imageLoader: ImageLoader,
    private val onClick: (VideoItem) -> Unit,
    private val onDelete: (VideoItem) -> Unit
) : ListAdapter<VideoRow, VideoAdapter.VH>(VideoRowDiff) {

    class Style(
        val bold: Typeface,
        val regular: Typeface,
        val red: Int,
        val gray: Int,
        val density: Float,
        val screenWidth: Int
    ) {
        fun dp(v: Int): Int = (v * density).toInt()
    }

    class VH(
        val card: LinearLayout,
        val thumb: ImageView,
        val title: TextView,
        val duration: TextView,
        val bar: ProgressBar,
        val delete: ImageButton
    ) : RecyclerView.ViewHolder(card)

    init { setHasStableIds(true) }

    override fun getItemId(position: Int): Long = getItem(position).video.id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx: Context = parent.context
        val s = style

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(s.dp(8), s.dp(8), s.dp(8), s.dp(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A1A")); cornerRadius = s.dp(14).toFloat()
            }
            layoutParams = RecyclerView.LayoutParams(-1, -2).apply {
                setMargins(s.dp(8), s.dp(6), s.dp(8), s.dp(6))
            }
        }

        val thumb = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, s.dp(10).toFloat())
                }
            }
        }
        val thumbW = s.screenWidth - s.dp(32)
        card.addView(thumb, LinearLayout.LayoutParams(-1, thumbW * 9 / 16))

        val bottom = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        card.addView(bottom, LinearLayout.LayoutParams(-1, -2).apply { topMargin = s.dp(8) })

        val info = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        bottom.addView(info, LinearLayout.LayoutParams(0, -2, 1f))

        val title = TextView(ctx).apply {
            setTextColor(Color.WHITE); textSize = 15f; typeface = s.bold
            maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        }
        info.addView(title)

        val duration = TextView(ctx).apply {
            setTextColor(s.gray); textSize = 12f; typeface = s.regular
            setPadding(0, s.dp(4), 0, 0)
        }
        info.addView(duration)

        val bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            progressTintList = ColorStateList.valueOf(s.red)
            visibility = View.GONE
        }
        info.addView(bar, LinearLayout.LayoutParams(-1, s.dp(6)).apply { topMargin = s.dp(6) })

        val delete = ImageButton(ctx).apply {
            setImageDrawable(IconDrawable(IconDrawable.TRASH, s.gray))
            background = null
            isFocusable = false
            contentDescription = "حذف الفيديو"
        }
        bottom.addView(delete, LinearLayout.LayoutParams(s.dp(40), s.dp(40)))

        val holder = VH(card, thumb, title, duration, bar, delete)
        card.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onClick(getItem(pos).video)
        }
        delete.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onDelete(getItem(pos).video)
        }
        return holder
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val row = getItem(position)
        val v = row.video

        h.title.text = v.title
        h.duration.text = formatDuration(v.duration)

        if (row.position > 0L && v.duration > 0L) {
            h.bar.max = v.duration.toInt()
            h.bar.progress = row.position.toInt()
            h.bar.visibility = View.VISIBLE
        } else {
            h.bar.visibility = View.GONE
        }

        // Coil يلغي الطلب تلقائيًا عند إعادة استخدام الـ ViewHolder، ويخزّن الصور في الذاكرة
        val w = style.screenWidth - style.dp(32)
        h.thumb.load(v.uri, imageLoader) {
            videoFrameMillis(1000)
            size(w, w * 9 / 16)
            placeholder(ColorDrawable(Color.parseColor("#2A2A2A")))
            error(ColorDrawable(Color.parseColor("#2A2A2A")))
            crossfade(150)
        }
    }

    private fun formatDuration(ms: Long): String {
        val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.US, "%02d:%02d", m, sec)
    }
}
