package com.beautycamera.util

import android.graphics.Bitmap

/**
 * Simple singleton that holds the last captured bitmap so it can be handed
 * directly to the Face/Body editor without writing to MediaStore first.
 * Call [consume] once — it returns the bitmap and clears the slot.
 */
object CapturedBitmapHolder {
    @Volatile var bitmap: Bitmap? = null

    fun set(bmp: Bitmap) { bitmap = bmp }

    fun consume(): Bitmap? = bitmap.also { bitmap = null }
}
