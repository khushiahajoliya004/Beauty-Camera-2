package com.beautycamera.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.beautycamera.domain.model.PhotoModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoRepository @Inject constructor() {

    suspend fun savePhoto(
        bitmap: Bitmap, 
        context: Context, 
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = 95
    ): String =
        withContext(Dispatchers.IO) {
            val extension = if (format == Bitmap.CompressFormat.PNG) "png" else "jpg"
            val mimeType = if (format == Bitmap.CompressFormat.PNG) "image/png" else "image/jpeg"
            val filename = "BeautyCamera_${System.currentTimeMillis()}.$extension"
            var outputStream: OutputStream? = null
            var uri: Uri? = null

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES + "/BeautyCamera"
                        )
                    }
                    val resolver = context.contentResolver
                    uri = resolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                    )
                    outputStream = uri?.let { resolver.openOutputStream(it) }
                } else {
                    val picturesDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES
                    )
                    val appDir = java.io.File(picturesDir, "BeautyCamera")
                    if (!appDir.exists()) appDir.mkdirs()
                    val file = java.io.File(appDir, filename)
                    outputStream = java.io.FileOutputStream(file)
                    uri = Uri.fromFile(file)
                }

                outputStream?.use {
                    bitmap.compress(format, quality, it)
                }

                uri?.toString() ?: ""
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
        }

    fun getDevicePhotos(context: Context): Flow<List<PhotoModel>> = flow {
        val photos = mutableListOf<PhotoModel>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, sortOrder
        )
        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val widthColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val uri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()
                )
                photos.add(
                    PhotoModel(
                        id = id,
                        uri = uri.toString(),
                        name = it.getString(nameColumn),
                        dateAdded = it.getLong(dateColumn),
                        width = it.getInt(widthColumn),
                        height = it.getInt(heightColumn)
                    )
                )
            }
        }
        emit(photos)
    }.flowOn(Dispatchers.IO)

    fun getPhotos(context: Context): Flow<List<PhotoModel>> = flow {
        val photos = mutableListOf<PhotoModel>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%BeautyCamera%")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, sortOrder
            )
        } else {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, sortOrder
            )
        }

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val widthColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val uri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )
                photos.add(
                    PhotoModel(
                        id = id,
                        uri = uri.toString(),
                        name = it.getString(nameColumn),
                        dateAdded = it.getLong(dateColumn),
                        width = it.getInt(widthColumn),
                        height = it.getInt(heightColumn)
                    )
                )
            }
        }
        emit(photos)
    }.flowOn(Dispatchers.IO)

    suspend fun deletePhoto(photoId: Long, context: Context): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    photoId.toString()
                )
                context.contentResolver.delete(uri, null, null) > 0
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
}
