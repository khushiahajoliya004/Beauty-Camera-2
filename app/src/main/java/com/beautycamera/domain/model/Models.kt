package com.beautycamera.domain.model

import android.graphics.Bitmap
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter

data class FilterModel(
    val id: String,
    val name: String,
    val filter: GPUImageFilter,
    val thumbnailBitmap: Bitmap? = null,
    val intensity: Float = 1.0f
)

data class StickerModel(
    val id: String,
    val assetPath: String,
    var x: Float = 0f,
    var y: Float = 0f,
    var scale: Float = 1f,
    var rotation: Float = 0f,
    var isSelected: Boolean = false,
    val emoji: String = ""
)

data class TextLayerModel(
    val id: String,
    var text: String,
    var x: Float = 0f,
    var y: Float = 0f,
    var scale: Float = 1f,
    var rotation: Float = 0f,
    var color: Int = android.graphics.Color.WHITE,
    var fontSize: Float = 40f,
    var fontAssetPath: String = "",
    var isSelected: Boolean = false
)

data class PhotoModel(
    val id: Long,
    val uri: String,
    val name: String,
    val dateAdded: Long,
    val width: Int,
    val height: Int
)

data class AIStyleTemplate(
    val id: String,
    val name: String,
    val prompt: String,
    val negativePrompt: String,
    val thumbnailResId: Int
)

data class BeautySettings(
    val skinSmoothing: Float = 0f,
    val lipColor: Int = android.graphics.Color.RED,
    val lipOpacity: Float = 0f,
    val blushIntensity: Float = 0f,
    val eyeColor: Int = android.graphics.Color.BLUE,
    val eyeColorOpacity: Float = 0f,
    val foundationIntensity: Float = 0f
)

data class BodySettings(
    val slimBody: Float = 0f,
    val slimWaist: Float = 0f,
    val legLengthening: Float = 0f,
    val bustEnhance: Float = 0f,
    val hipEnhance: Float = 0f,
    val shoulderReshape: Float = 0f
)

data class FaceLandmarks(
    val points: List<Pair<Float, Float>>,
    val imageWidth: Int,
    val imageHeight: Int
)

data class PoseLandmarks(
    val points: List<Triple<Float, Float, Float>>,
    val imageWidth: Int,
    val imageHeight: Int
)
