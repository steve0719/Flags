package uz.bayroq

import android.content.Context
import android.graphics.*
import android.view.View

class FlagOverlay(context: Context) : View(context) {
    var flag: String = ""
        set(v) { field = v; invalidate() }

    private var face: RectF? = null
    private var srcW = 1; private var srcH = 1
    private var cx = 0f; private var cy = 0f; private var size = 0f
    private var hasPos = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    fun update(box: Rect?, w: Int, h: Int) {
        srcW = w; srcH = h
        face = box?.let { RectF(it) }
        if (box == null) hasPos = false
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val f = face ?: return
        if (flag.isEmpty() || width == 0) return
        val scale = maxOf(width / srcW.toFloat(), height / srcH.toFloat())
        val dx = (width - srcW * scale) / 2f
        val dy = (height - srcH * scale) / 2f
        val fw = f.width() * scale
        val tx = width - (f.centerX() * scale + dx)
        val ts = fw * 0.66f
        val ty = f.top * scale + dy - ts * 0.35f
        if (!hasPos) { cx = tx; cy = ty; size = ts; hasPos = true }
        else { cx += (tx - cx) * 0.45f; cy += (ty - cy) * 0.45f; size += (ts - size) * 0.3f }
        paint.textSize = size
        c.drawText(flag, cx, cy + size * 0.35f, paint)
        postInvalidateOnAnimation()
    }
}
