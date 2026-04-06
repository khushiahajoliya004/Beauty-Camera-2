package com.beautycamera.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import com.beautycamera.data.repository.SDRepository
import com.beautycamera.data.repository.FilterRepository
import com.beautycamera.data.repository.PhotoRepository
import com.beautycamera.data.repository.StickerRepository
import com.beautycamera.domain.model.AIStyleTemplate
import com.beautycamera.domain.model.BeautySettings
import com.beautycamera.domain.model.BodySettings
import com.beautycamera.domain.model.FilterModel
import com.beautycamera.domain.model.PhotoModel
import com.beautycamera.domain.model.StickerModel
import com.beautycamera.util.BitmapUtils
import com.beautycamera.util.GPUImageHelper
import com.beautycamera.util.OpenCVHelper
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetFiltersUseCase @Inject constructor(
    private val filterRepository: FilterRepository
) {
    operator fun invoke(): List<FilterModel> = filterRepository.getAllFilters()
}

class GetStickersUseCase @Inject constructor(
    private val stickerRepository: StickerRepository
) {
    operator fun invoke(): List<StickerModel> = stickerRepository.getAllStickers()
}

class SavePhotoUseCase @Inject constructor(
    private val photoRepository: PhotoRepository
) {
    suspend operator fun invoke(bitmap: Bitmap, context: Context): String =
        photoRepository.savePhoto(bitmap, context)
}

class GetPhotosUseCase @Inject constructor(
    private val photoRepository: PhotoRepository
) {
    operator fun invoke(context: Context): Flow<List<PhotoModel>> =
        photoRepository.getPhotos(context)
}

class ApplyBeautyUseCase @Inject constructor(
    private val gpuImageHelper: GPUImageHelper,
    private val bitmapUtils: BitmapUtils
) {
    suspend operator fun invoke(bitmap: Bitmap, settings: BeautySettings): Bitmap =
        gpuImageHelper.applyBeautyEffects(bitmap, settings)
}

class ApplyBodyReshapeUseCase @Inject constructor(
    private val openCVHelper: OpenCVHelper
) {
    suspend operator fun invoke(bitmap: Bitmap, settings: BodySettings): Bitmap =
        openCVHelper.applyBodyReshape(bitmap, settings)
}

class GenerateAIPortraitUseCase @Inject constructor(
    private val sdRepository: SDRepository
) {
    suspend operator fun invoke(
        bitmap: Bitmap,
        template: AIStyleTemplate,
        onStatusUpdate: (String) -> Unit = {},
    ): Result<Bitmap> = sdRepository.generateFromTemplate(bitmap, template, onStatusUpdate = onStatusUpdate)
}

class DeletePhotoUseCase @Inject constructor(
    private val photoRepository: PhotoRepository
) {
    suspend operator fun invoke(photoId: Long, context: Context): Boolean =
        photoRepository.deletePhoto(photoId, context)
}
