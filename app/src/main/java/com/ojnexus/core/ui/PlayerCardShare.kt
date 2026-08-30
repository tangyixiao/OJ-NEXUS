package com.ojnexus.core.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import com.ojnexus.core.designsystem.NexusColors
import java.io.File

data class PlayerCardImageData(
    val title: String,
    val role: String,
    val cardLabel: String,
    val achievementsLabel: String,
    val solvedLabel: String,
    val solvedValue: String,
    val attemptsLabel: String,
    val attemptsValue: String,
    val activeDaysLabel: String,
    val activeDaysValue: String,
    val streakLabel: String,
    val streakValue: String,
    val maxDifficultyLabel: String,
    val maxDifficultyValue: String,
    val achievements: List<String>,
)

/** Renders and shares the Player Card using the same dark palette as Compose. */
object PlayerCardShare {
    fun share(context: Context, data: PlayerCardImageData) {
        val file = renderToCache(context, data) ?: return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(intent, data.title))
        } catch (_: ActivityNotFoundException) {
            // No share target is available; Profile remains usable.
        }
    }

    internal fun renderToCache(context: Context, data: PlayerCardImageData): File? = runCatching {
        val colors = NexusColors.dark()
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(colors.background.toArgb())
        canvas.drawRect(PADDING, PADDING, CARD_WIDTH - PADDING, CARD_HEIGHT - PADDING, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.surface.toArgb()
        })

        val mono = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        val bold = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        drawText(canvas, data.title, PADDING + CONTENT_INSET, 190f, 62f, colors.textPrimary.toArgb(), bold)
        drawText(canvas, data.role, PADDING + CONTENT_INSET, 245f, 24f, colors.textTertiary.toArgb(), mono)
        drawText(canvas, data.cardLabel, PADDING + CONTENT_INSET, 305f, 20f, colors.accent.toArgb(), bold)

        listOf(
            data.solvedLabel to data.solvedValue,
            data.attemptsLabel to data.attemptsValue,
            data.activeDaysLabel to data.activeDaysValue,
            data.streakLabel to data.streakValue,
            data.maxDifficultyLabel to data.maxDifficultyValue,
        ).forEachIndexed { index, (label, value) ->
            val x = PADDING + CONTENT_INSET + (index % METRICS_PER_ROW) * METRIC_WIDTH
            val y = 400f + (index / METRICS_PER_ROW) * 165f
            drawText(canvas, label, x, y, 20f, colors.textTertiary.toArgb(), mono)
            drawText(canvas, value, x, y + 65f, 42f, colors.textPrimary.toArgb(), bold)
        }

        val achievementY = 780f
        drawText(canvas, data.achievementsLabel, PADDING + CONTENT_INSET, achievementY, 20f, colors.textTertiary.toArgb(), mono)
        data.achievements.take(MAX_ACHIEVEMENTS).forEachIndexed { index, achievement ->
            drawText(canvas, "• $achievement", PADDING + CONTENT_INSET, achievementY + 65f + index * 52f, 25f, colors.success.toArgb(), bold)
        }

        File(context.cacheDir, "oj-nexus-player-card.png").also { output ->
            output.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
        }
    }.getOrNull()

    private fun drawText(canvas: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, typeface: Typeface) {
        canvas.drawText(value, x, y, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            this.typeface = typeface
        })
    }

    private const val CARD_WIDTH = 1080
    private const val CARD_HEIGHT = 1350
    private const val PADDING = 72f
    private const val CONTENT_INSET = 56f
    private const val METRICS_PER_ROW = 2
    private const val METRIC_WIDTH = 430f
    private const val MAX_ACHIEVEMENTS = 5
}
