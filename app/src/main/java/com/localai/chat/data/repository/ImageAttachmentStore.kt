package com.localai.chat.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ImageAttachmentStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun copyFromUri(uri: Uri): ImageAttachment = withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        val displayName = queryDisplayName(uri) ?: "image${extensionForMimeType(mimeType)}"
        val target = createTargetFile(extensionForMimeType(mimeType))

        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to read selected image." }
            target.outputStream().use { output -> input.copyTo(output) }
        }
        normalizeImageOrientation(target, mimeType)

        ImageAttachment(
            id = UUID.randomUUID().toString(),
            localPath = target.absolutePath,
            mimeType = mimeType,
            displayName = displayName,
        )
    }

    fun createCameraCaptureTarget(): PendingCameraCapture {
        val target = createTargetFile(".jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            target,
        )
        return PendingCameraCapture(
            uri = uri,
            localPath = target.absolutePath,
            displayName = target.name,
        )
    }

    suspend fun completeCameraCapture(target: PendingCameraCapture): ImageAttachment = withContext(Dispatchers.IO) {
        val file = File(target.localPath)
        require(file.exists() && file.length() > 0L) { "Camera did not save an image." }
        normalizeImageOrientation(file, "image/jpeg")
        ImageAttachment(
            id = UUID.randomUUID().toString(),
            localPath = file.absolutePath,
            mimeType = "image/jpeg",
            displayName = target.displayName,
        )
    }

    suspend fun delete(localPath: String) = withContext(Dispatchers.IO) {
        runCatching { File(localPath).delete() }
    }

    private fun createTargetFile(extension: String): File {
        val directory = File(context.filesDir, AttachmentDirectory).also { it.mkdirs() }
        return File(directory, "${System.currentTimeMillis()}-${UUID.randomUUID()}$extension")
    }

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.getString(0)
            }
            ?.takeIf(String::isNotBlank)
    }

    private fun normalizeImageOrientation(
        file: File,
        mimeType: String,
    ) {
        if (!mimeType.equals("image/jpeg", ignoreCase = true)) return
        val exif = runCatching { ExifInterface(file.absolutePath) }.getOrNull() ?: return
        val matrix = orientationMatrix(exif) ?: return

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, MaxStoredImagePixels)
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: return
        val normalized = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        file.outputStream().use { output ->
            normalized.compress(Bitmap.CompressFormat.JPEG, JpegQuality, output)
        }
        if (normalized != bitmap) normalized.recycle()
        bitmap.recycle()

        runCatching {
            ExifInterface(file.absolutePath).run {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                saveAttributes()
            }
        }
    }

    private fun orientationMatrix(exif: ExifInterface): Matrix? {
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
        if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) {
            return null
        }

        return Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> preScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> preScale(1f, -1f)
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    postRotate(90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    postRotate(270f)
                    postScale(-1f, 1f)
                }
            }
        }
    }

    private fun calculateSampleSize(
        width: Int,
        height: Int,
        maxPixels: Int,
    ): Int {
        if (width <= 0 || height <= 0) return 1
        var sampleSize = 1
        while (width / sampleSize > maxPixels || height / sampleSize > maxPixels) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun extensionForMimeType(mimeType: String): String {
        return when (mimeType.lowercase()) {
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            "image/gif" -> ".gif"
            else -> ".jpg"
        }
    }

    private companion object {
        const val AttachmentDirectory = "image_attachments"
        const val JpegQuality = 92
        const val MaxStoredImagePixels = 4096
    }
}

data class PendingCameraCapture(
    val uri: Uri,
    val localPath: String,
    val displayName: String,
)
