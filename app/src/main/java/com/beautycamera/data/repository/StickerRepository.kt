package com.beautycamera.data.repository

import android.content.Context
import com.beautycamera.domain.model.StickerModel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StickerRepository @Inject constructor(private val context: Context) {

    fun getAllStickers(): List<StickerModel> {
        // Try loading PNG/WebP from assets first
        val assetStickers = try {
            val files = context.assets.list("stickers") ?: emptyArray()
            files.filter { it.endsWith(".png") || it.endsWith(".webp") }
                .mapIndexed { index, filename ->
                    StickerModel(id = "sticker_$index", assetPath = "stickers/$filename")
                }
        } catch (e: Exception) { emptyList() }

        if (assetStickers.isNotEmpty()) return assetStickers

        // Fallback: built-in emoji stickers (no asset files needed)
        val emojis = listOf(
            "😍", "😘", "🥰", "😎", "🤩", "😜", "🥳", "😇",
            "✨", "💖", "💫", "❤️", "💕", "💯", "🔥", "🎉",
            "🌸", "🌺", "🦋", "🌈", "⭐", "🌙", "☀️", "🍀",
            "🎀", "👑", "💄", "💅", "👗", "🕶️", "💎", "🌻"
        )
        return emojis.mapIndexed { index, emoji ->
            StickerModel(id = "emoji_$index", assetPath = "", emoji = emoji)
        }
    }
}
