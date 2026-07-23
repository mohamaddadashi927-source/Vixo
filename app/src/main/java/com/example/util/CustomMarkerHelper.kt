package com.example.util

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

object CustomMarkerHelper {

    fun createRideMarker(
        context: Context,
        titleText: String,
        isOrigin: Boolean
    ): Drawable {
        val density = context.resources.displayMetrics.density

        val badgeHeightPx = (26 * density).toInt()
        val badgePaddingPx = (12 * density).toInt()
        val tailHeightPx = (10 * density).toInt()

        val primaryColor = if (isOrigin) 0xFF10B981.toInt() else 0xFFEF4444.toInt()
        val darkBgColor = 0xFF0F172A.toInt()

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 12f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val textWidth = textPaint.measureText(titleText)
        val badgeWidthPx = (textWidth + badgePaddingPx * 2).coerceAtLeast(64f * density).toInt()

        val width = badgeWidthPx + (12 * density).toInt()
        val height = badgeHeightPx + tailHeightPx + (24 * density).toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val centerX = width / 2f
        val topY = 4f * density

        // Draw Shadow at bottom
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(45, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawOval(
            centerX - (10 * density),
            height - (8 * density),
            centerX + (10 * density),
            height - (2 * density),
            shadowPaint
        )

        // Draw Badge Background
        val badgeRect = RectF(
            centerX - badgeWidthPx / 2f,
            topY,
            centerX + badgeWidthPx / 2f,
            topY + badgeHeightPx
        )
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkBgColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(badgeRect, 13f * density, 13f * density, badgePaint)

        // Draw Badge Accent Border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
        }
        canvas.drawRoundRect(badgeRect, 13f * density, 13f * density, borderPaint)

        // Draw Badge Label Text
        val fontMetrics = textPaint.fontMetrics
        val textY = badgeRect.centerY() - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(titleText, centerX, textY, textPaint)

        // Draw Pin Head & Dot below Badge
        val pinCenterY = badgeRect.bottom + tailHeightPx / 2f + 4f * density
        val pinBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, pinCenterY, 10f * density, pinBgPaint)

        // Inner White Center Dot
        val whiteDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, pinCenterY, 4.5f * density, whiteDotPaint)

        return BitmapDrawable(context.resources, bitmap)
    }
}
