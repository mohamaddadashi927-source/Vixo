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

    fun createStationMarker(
        context: Context,
        label: String,
        isBoarding: Boolean
    ): Drawable {
        val density = context.resources.displayMetrics.density

        val badgeHeightPx = (24 * density).toInt()
        val badgePaddingPx = (10 * density).toInt()
        val primaryColor = if (isBoarding) 0xFF059669.toInt() else 0xFFDC2626.toInt()

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 11f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val textWidth = textPaint.measureText(label)
        val badgeWidthPx = (textWidth + badgePaddingPx * 2).toInt()

        val width = badgeWidthPx + (8 * density).toInt()
        val height = badgeHeightPx + (18 * density).toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val centerX = width / 2f
        val topY = 2f * density

        val badgeRect = RectF(
            centerX - badgeWidthPx / 2f,
            topY,
            centerX + badgeWidthPx / 2f,
            topY + badgeHeightPx
        )

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(badgeRect, 10f * density, 10f * density, badgePaint)

        val fontMetrics = textPaint.fontMetrics
        val textY = badgeRect.centerY() - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(label, centerX, textY, textPaint)

        // Station Pin Point
        val pinY = badgeRect.bottom + 6f * density
        canvas.drawCircle(centerX, pinY, 6f * density, badgePaint)
        val innerWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawCircle(centerX, pinY, 2.5f * density, innerWhite)

        return BitmapDrawable(context.resources, bitmap)
    }

    fun createLiveBusMarker(
        context: Context,
        busNumber: String,
        colorHex: String = "#2563EB"
    ): Drawable {
        val density = context.resources.displayMetrics.density
        val sizePx = (38 * density).toInt()

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val center = sizePx / 2f
        val radius = (16 * density)

        val busColor = try {
            Color.parseColor(colorHex)
        } catch (e: Exception) {
            0xFF2563EB.toInt()
        }

        // Shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 0, 0, 0)
        }
        canvas.drawCircle(center, center + (2 * density), radius, shadowPaint)

        // Main Circle
        val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = busColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius, mainPaint)

        // White Ring
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
        }
        canvas.drawCircle(center, center, radius - 1.2f * density, ringPaint)

        // Direction Pointer Arrow (Top of circle indicating heading direction)
        val pointerPath = android.graphics.Path().apply {
            moveTo(center, center - radius - (4f * density))
            lineTo(center - (5f * density), center - radius + (2f * density))
            lineTo(center + (5f * density), center - radius + (2f * density))
            close()
        }
        val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = busColor
            style = Paint.Style.FILL
        }
        canvas.drawPath(pointerPath, pointerPaint)

        // Bus Line Text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 11f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val fontMetrics = textPaint.fontMetrics
        val textY = center - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText("🚌 $busNumber", center, textY, textPaint)

        return BitmapDrawable(context.resources, bitmap)
    }
}
