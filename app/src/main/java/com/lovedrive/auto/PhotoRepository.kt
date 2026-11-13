package com.lovedrive.auto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class LovePhoto(
    val id: String,
    val file: File,
    val index: Int,
    val total: Int
)

data class AnniversaryStats(
    val daysTogether: Long,
    val daysToNextAnniversary: Long,
    val nextAnniversaryNumber: Long,
    val isAnniversaryToday: Boolean,
    val nextDateFormatted: String
)

data class BirthdayEntry(
    val month: Int,
    val day: Int,
    val name: String
)

data class BirthdayReminder(
    val name: String,
    val daysUntil: Long,
    val dateFormatted: String,
    val isToday: Boolean
)

object PhotoRepository {
    private const val PREFS = "love_drive_prefs"
    private const val KEY_SUBTITLE = "subtitle"
    private const val KEY_INTERVAL_SEC = "interval_sec"
    private const val KEY_MARRIAGE_DATE = "marriage_date"
    private const val KEY_HIDE_SEEKBAR = "hide_seekbar"
    private const val DEFAULT_MARRIAGE_DATE = "2024-01-21"
    private const val PHOTO_DIR = "love_drive_photos"

    private val BIRTHDAYS = listOf(
        BirthdayEntry(4, 21, "Kayalvizhi (Wife)"),
        BirthdayEntry(5, 17, "Sidique"),
        BirthdayEntry(5, 20, "Mona"),
        BirthdayEntry(5, 21, "Ragav & Renuga"),
        BirthdayEntry(5, 31, "Saravana (My Bday)"),
        BirthdayEntry(6, 4, "Sasi Sithi"),
        BirthdayEntry(6, 6, "Ammu Sithi"),
        BirthdayEntry(6, 29, "Felix"),
        BirthdayEntry(8, 21, "Yoga"),
        BirthdayEntry(10, 7, "Amma"),
        BirthdayEntry(10, 20, "Surya"),
        BirthdayEntry(12, 29, "Appa")
    )

    fun getNextBirthdayReminder(): BirthdayReminder {
        val today = LocalDate.now()
        var best: BirthdayReminder? = null

        for (b in BIRTHDAYS) {
            var target = LocalDate.of(today.year, b.month, b.day)
            if (target.isBefore(today)) {
                target = target.plusYears(1)
            }
            val days = ChronoUnit.DAYS.between(today, target)
            if (best == null || days < best.daysUntil) {
                best = BirthdayReminder(
                    name = b.name,
                    daysUntil = days,
                    dateFormatted = target.format(DateTimeFormatter.ofPattern("MMM dd")),
                    isToday = (days == 0L)
                )
            }
        }
        return best!!
    }

    fun getPhotoDir(context: Context): File {
        val dir = File(context.filesDir, PHOTO_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getPhotos(context: Context): List<LovePhoto> {
        val dir = getPhotoDir(context)
        val files = dir.listFiles { f ->
            f.isFile && (f.name.endsWith(".jpg", true) || f.name.endsWith(".png", true))
        }?.sortedBy { it.name } ?: emptyList()

        return files.mapIndexed { index, file ->
            LovePhoto(
                id = file.name,
                file = file,
                index = index + 1,
                total = files.size
            )
        }
    }

    fun getTimeGreeting(wifeName: String = "Kayalvizhi"): String {
        val bday = getNextBirthdayReminder()
        if (bday.isToday) {
            return "🎂 Happy Birthday, ${bday.name}!"
        }
        val hour = LocalTime.now().hour
        return when (hour) {
            in 5..11 -> "Good Morning, $wifeName ☀️"
            in 12..16 -> "Good Afternoon, $wifeName 🌤️"
            in 17..20 -> "Good Evening, $wifeName ✨"
            else -> "Sweet Night Drive, $wifeName 🌙"
        }
    }

    fun getAnniversaryStats(context: Context): AnniversaryStats {
        val dateStr = getMarriageDate(context)
        val marriageDate = try {
            LocalDate.parse(dateStr)
        } catch (_: Exception) {
            LocalDate.of(2024, 1, 21)
        }
        val today = LocalDate.now()
        val daysTogether = ChronoUnit.DAYS.between(marriageDate, today).coerceAtLeast(0)

        var nextAnniv = marriageDate.withYear(today.year)
        if (nextAnniv.isBefore(today)) {
            nextAnniv = nextAnniv.plusYears(1)
        }
        val daysToNext = ChronoUnit.DAYS.between(today, nextAnniv).coerceAtLeast(0)
        val nextNumber = ChronoUnit.YEARS.between(marriageDate, nextAnniv).coerceAtLeast(1)
        val isToday = (daysToNext == 0L)
        val formatted = nextAnniv.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))

        return AnniversaryStats(
            daysTogether = daysTogether,
            daysToNextAnniversary = daysToNext,
            nextAnniversaryNumber = nextNumber,
            isAnniversaryToday = isToday,
            nextDateFormatted = formatted
        )
    }

    fun formatOrdinal(n: Long): String {
        val mod100 = n % 100
        if (mod100 in 11..13) return "${n}th"
        return when (n % 10) {
            1L -> "${n}st"
            2L -> "${n}nd"
            3L -> "${n}rd"
            else -> "${n}th"
        }
    }

    fun importUris(context: Context, uris: List<Uri>) {
        val dir = getPhotoDir(context)
        uris.forEachIndexed { idx, uri ->
            try {
                val bitmap = decodeAndResizeFromUri(context, uri, 800) ?: return@forEachIndexed
                val fileName = "photo_${System.currentTimeMillis()}_$idx.jpg"
                val outFile = File(dir, fileName)
                FileOutputStream(outFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                }
                bitmap.recycle()
            } catch (_: Exception) {
                // Skip unreadable image
            }
        }
    }

    fun deletePhoto(photo: LovePhoto) {
        if (photo.file.exists()) {
            photo.file.delete()
        }
    }

    fun loadCarBitmap(
        file: File,
        targetW: Int = 800,
        overlayBadge: String? = null,
        targetH: Int = targetW
    ): Bitmap? {
        val maxDim = maxOf(targetW, targetH)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDim * 2 || bounds.outHeight / sampleSize > maxDim * 2) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val raw = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        return blurFitFrame(raw, targetW, targetH, overlayBadge)
    }

    private fun blurFitFrame(
        src: Bitmap,
        targetW: Int,
        targetH: Int,
        overlayBadge: String?
    ): Bitmap {
        val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        // Multi-pass smooth blur background filling the entire targetW x targetH frame
        val pass1 = Bitmap.createScaledBitmap(src, 48, 48, true)
        val pass2 = Bitmap.createScaledBitmap(pass1, 20, 20, true)
        pass1.recycle()
        val bgScale = maxOf(targetW.toFloat() / 20f, targetH.toFloat() / 20f)
        val bgW = 20f * bgScale
        val bgH = 20f * bgScale
        val bgLeft = (targetW - bgW) / 2f
        val bgTop = (targetH - bgH) / 2f
        canvas.drawBitmap(
            pass2,
            Rect(0, 0, 20, 20),
            RectF(bgLeft, bgTop, bgLeft + bgW, bgTop + bgH),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )
        pass2.recycle()

        val dimPaint = Paint().apply { color = Color.argb(100, 10, 8, 14) }
        canvas.drawRect(0f, 0f, targetW.toFloat(), targetH.toFloat(), dimPaint)

        // Draw full uncropped photo centered with soft rounded corners
        val scale = minOf(targetW.toFloat() / src.width, targetH.toFloat() / src.height)
        val drawW = src.width * scale
        val drawH = src.height * scale
        val left = (targetW - drawW) / 2f
        val top = (targetH - drawH) / 2f
        val dstRect = RectF(left, top, left + drawW, top + drawH)

        val saveCount = canvas.save()
        val clipPath = Path().apply {
            val radius = minOf(drawW, drawH) * 0.03f
            addRoundRect(dstRect, radius, radius, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)
        canvas.drawBitmap(src, Rect(0, 0, src.width, src.height), dstRect, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restoreToCount(saveCount)

        if (!overlayBadge.isNullOrBlank()) {
            val minSide = minOf(targetW, targetH)
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = minSide * 0.036f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val pillH = minSide * 0.085f
            val pillMargin = minSide * 0.035f
            val pillRect = RectF(
                pillMargin,
                targetH - pillH - pillMargin,
                targetW - pillMargin,
                targetH - pillMargin
            )
            val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(175, 24, 12, 22)
            }
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillPaint)

            val fm = textPaint.fontMetrics
            val textY = pillRect.centerY() - (fm.ascent + fm.descent) / 2f
            canvas.drawText(overlayBadge, pillRect.centerX(), textY, textPaint)
        }

        src.recycle()
        return out
    }

    private fun decodeAndResizeFromUri(context: Context, uri: Uri, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDim * 2 || bounds.outHeight / sampleSize > maxDim * 2) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null

        val rotated = applyExifRotation(context, uri, decoded)
        val scaled = scaleDownToMax(rotated, maxDim)
        if (scaled !== rotated) rotated.recycle()
        return scaled
    }

    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL

            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f) return bitmap
            val matrix = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            rotated
        } catch (_: Exception) {
            bitmap
        }
    }

    private fun scaleDownToMax(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
        return Bitmap.createScaledBitmap(bitmap, (w * ratio).toInt(), (h * ratio).toInt(), true)
    }

    fun getSubtitle(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SUBTITLE, "Front Seat Reserved for Passenger Princess 👑❤️")
            ?: "Front Seat Reserved for Passenger Princess 👑❤️"

    fun setSubtitle(context: Context, subtitle: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SUBTITLE, subtitle.trim())
            .apply()
    }

    fun getMarriageDate(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MARRIAGE_DATE, DEFAULT_MARRIAGE_DATE) ?: DEFAULT_MARRIAGE_DATE

    fun setMarriageDate(context: Context, isoDate: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MARRIAGE_DATE, isoDate)
            .apply()
    }

    fun getHideSeekbar(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_HIDE_SEEKBAR, true)

    fun setHideSeekbar(context: Context, hide: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HIDE_SEEKBAR, hide)
            .apply()
    }

    fun getIntervalSeconds(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_INTERVAL_SEC, 8)

    fun setIntervalSeconds(context: Context, seconds: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_INTERVAL_SEC, seconds.coerceIn(5, 60))
            .apply()
    }
}
