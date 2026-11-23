package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

data class ProcessedPhoto(
    val aiBitmap: Bitmap,
    val thumbnailBase64: String
)

object ImageUtils {

    fun createTempCameraUri(context: Context): Uri {
        val dir = File(context.cacheDir, "captured_hazards").apply { mkdirs() }
        val file = File(dir, "hazard_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    suspend fun processSelectedPhoto(context: Context, uri: Uri): ProcessedPhoto? =
        withContext(Dispatchers.IO) {
            try {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOpts)
                }
                val sampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, 1024, 1024)
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                } ?: return@withContext null

                val aiBitmap = scaleBitmapMax(decoded, 1024)
                val thumbBitmap = scaleBitmapMax(aiBitmap, 420)
                val baos = ByteArrayOutputStream()
                var quality = 72
                thumbBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                while (baos.size() > 160_000 && quality > 30) {
                    baos.reset()
                    quality -= 12
                    thumbBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                }
                val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                ProcessedPhoto(aiBitmap = aiBitmap, thumbnailBase64 = base64)
            } catch (_: Exception) {
                null
            }
        }

    fun decodeBase64Bitmap(base64: String?): Bitmap? {
        if (base64.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqW: Int, reqH: Int): Int {
        var inSampleSize = 1
        if (height > reqH || width > reqW) {
            val halfH = height / 2
            val halfW = width / 2
            while ((halfH / inSampleSize) >= reqH && (halfW / inSampleSize) >= reqW) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun scaleBitmapMax(src: Bitmap, maxDim: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= maxDim && h <= maxDim) return src
        val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
        val newW = (w * ratio).toInt().coerceAtLeast(1)
        val newH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, newW, newH, true)
    }
}
