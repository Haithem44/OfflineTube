
inner class VideoAdapter(
    private val videos: List<Vid>,
    private val onClick: (Vid) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VH>() {

    inner class VH(
        val frame: FrameLayout,
        val image: ImageView,
        val duration: TextView
    ) : RecyclerView.ViewHolder(frame)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): VH {

        val frame = FrameLayout(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(220)
            ).apply {
                setMargins(dp(4), dp(5), dp(4), dp(12))
            }
            setBackgroundColor(Color.BLACK)
        }

        val image = ImageView(parent.context).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#222222"))
        }
        frame.addView(image)

        val duration = TextView(parent.context).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            setBackgroundColor(Color.parseColor("#CC000000"))
            setPadding(dp(6), dp(3), dp(6), dp(3))
            layoutParams = FrameLayout.LayoutParams(
                -2, -2, Gravity.BOTTOM or Gravity.END
            ).apply {
                setMargins(0, 0, dp(8), dp(8))
            }
        }
        frame.addView(duration)

        return VH(frame, image, duration)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val vid = videos[position]

        holder.duration.text = formatDuration(vid.duration)
        holder.frame.setOnClickListener { onClick(vid) }

        val cached = ThumbnailCache.get(vid.id)

        if (cached != null) {
            holder.image.setImageBitmap(cached)
        } else {
            holder.image.setImageResource(
                android.R.color.darker_gray
            )

            pool.execute {
                try {
                    val bitmap = if (Build.VERSION.SDK_INT >= 29) {
                        contentResolver.loadThumbnail(
                            vid.uri, Size(640, 360), null
                        )
                    } else {
                        MediaStore.Video.Thumbnails.getThumbnail(
                            contentResolver,
                            vid.id,
                            MediaStore.Video.Thumbnails.MINI_KIND,
                            null
                        )
                    }

                    if (bitmap != null) {
                        ThumbnailCache.put(vid.id, bitmap)

                        runOnUiThread {
                            val p = holder.bindingAdapterPosition
                            if (p != RecyclerView.NO_POSITION &&
                                videos[p].id == vid.id
                            ) {
                                holder.image.setImageBitmap(bitmap)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    override fun getItemCount() = videos.size
}
