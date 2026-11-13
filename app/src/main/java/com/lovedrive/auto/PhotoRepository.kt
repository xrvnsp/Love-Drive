package com.lovedrive.auto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
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

object PhotoRepository {
    private const val PREFS = "love_drive_prefs"
    private const val KEY_SUBTITLE = "subtitle"
    private const val KEY_INTERVAL_SEC = "interval_sec"
    private const val KEY_MARRIAGE_DATE = "marriage_date"
    private const val KEY_HIDE_SEEKBAR = "hide_seekbar"
    private const val DEFAULT_MARRIAGE_DATE = "2024-01-21"
    private const val PHOTO_DIR = "love_drive_photos"

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

    fun loadCarBitmap(file: File, maxDim: Int = 800): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDim * 2 || bounds.outHeight / sampleSize > maxDim * 2) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val raw = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        return centerSquareCrop(raw, maxDim)
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

    private fun centerSquareCrop(bitmap: Bitmap, targetSize: Int): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2
        val cropped = Bitmap.createBitmap(bitmap, x, y, size, size)
        val scaled = Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
        if (cropped !== bitmap && cropped !== scaled) cropped.recycle()
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    fun getSubtitle(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SUBTITLE, "Saravana ❤️ Kayalvizhi") ?: "Saravana ❤️ Kayalvizhi"

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
