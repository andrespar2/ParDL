package com.andrespar2.pardl.core

import android.content.Context
import android.content.SharedPreferences
import com.andrespar2.pardl.core.music.QualityPolicy

/** Preferencias persistentes de ParDL. */
class Prefs private constructor(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("pardl_prefs", Context.MODE_PRIVATE)

    /** URI (SAF) de la carpeta de descargas elegida por el usuario. */
    var treeUri: String?
        get() = sp.getString(KEY_TREE_URI, null)
        set(value) = sp.edit().putString(KEY_TREE_URI, value).apply()

    /** Nombre visible de la carpeta de descargas. */
    var folderName: String?
        get() = sp.getString(KEY_FOLDER_NAME, null)
        set(value) = sp.edit().putString(KEY_FOLDER_NAME, value).apply()

    // ------------------------------------------------------------------
    // Apariencia
    // ------------------------------------------------------------------

    /** system | dark | light | amoled */
    var themeMode: String
        get() = sp.getString(KEY_THEME_MODE, "dark") ?: "dark"
        set(value) = sp.edit().putString(KEY_THEME_MODE, value).apply()

    var glassBlur: Float
        get() = sp.getFloat(KEY_GLASS_BLUR, 0.65f)
        set(value) = sp.edit().putFloat(KEY_GLASS_BLUR, value.coerceIn(0f, 1f)).apply()

    var animationsEnabled: Boolean
        get() = sp.getBoolean(KEY_ANIMATIONS, true)
        set(value) = sp.edit().putBoolean(KEY_ANIMATIONS, value).apply()

    // ------------------------------------------------------------------
    // Música
    // ------------------------------------------------------------------

    /** Proveedor preferido inicial: TIDAL, aunque hoy no pueda descargar. */
    var musicPreferredProvider: String
        get() = sp.getString(KEY_MUSIC_PREFERRED, "tidal") ?: "tidal"
        set(value) = sp.edit().putString(KEY_MUSIC_PREFERRED, value).apply()

    var musicProviderOrder: String
        get() = sp.getString(
            KEY_MUSIC_ORDER,
            "tidal,internet-archive,deezer,apple-music,musicbrainz,lrclib,direct-url,spotify-web"
        ) ?: "tidal,internet-archive,deezer,apple-music,musicbrainz,lrclib,direct-url,spotify-web"
        set(value) = sp.edit().putString(KEY_MUSIC_ORDER, value).apply()

    var musicDisabledProviders: Set<String>
        get() = sp.getStringSet(KEY_MUSIC_DISABLED, emptySet())?.toSet() ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY_MUSIC_DISABLED, value).apply()

    var musicFlacFirst: Boolean
        get() = sp.getBoolean(KEY_FLAC_FIRST, true)
        set(value) = sp.edit().putBoolean(KEY_FLAC_FIRST, value).apply()

    var musicHiResFirst: Boolean
        get() = sp.getBoolean(KEY_HIRES_FIRST, true)
        set(value) = sp.edit().putBoolean(KEY_HIRES_FIRST, value).apply()

    var musicPreferSpatial: Boolean
        get() = sp.getBoolean(KEY_PREFER_SPATIAL, false)
        set(value) = sp.edit().putBoolean(KEY_PREFER_SPATIAL, value).apply()

    var musicShowAll: Boolean
        get() = sp.getBoolean(KEY_SHOW_ALL, true)
        set(value) = sp.edit().putBoolean(KEY_SHOW_ALL, value).apply()

    var musicAllowLossy: Boolean
        get() = sp.getBoolean(KEY_ALLOW_LOSSY, true)
        set(value) = sp.edit().putBoolean(KEY_ALLOW_LOSSY, value).apply()

    var musicLosslessOnly: Boolean
        get() = sp.getBoolean(KEY_LOSSLESS_ONLY, false)
        set(value) = sp.edit().putBoolean(KEY_LOSSLESS_ONLY, value).apply()

    var musicAskOnChange: Boolean
        get() = sp.getBoolean(KEY_ASK_ON_CHANGE, true)
        set(value) = sp.edit().putBoolean(KEY_ASK_ON_CHANGE, value).apply()

    var musicEmbedMetadata: Boolean
        get() = sp.getBoolean(KEY_EMBED_METADATA, true)
        set(value) = sp.edit().putBoolean(KEY_EMBED_METADATA, value).apply()

    var musicEmbedCover: Boolean
        get() = sp.getBoolean(KEY_EMBED_COVER, true)
        set(value) = sp.edit().putBoolean(KEY_EMBED_COVER, value).apply()

    var musicEmbedLyrics: Boolean
        get() = sp.getBoolean(KEY_EMBED_LYRICS, false)
        set(value) = sp.edit().putBoolean(KEY_EMBED_LYRICS, value).apply()

    var musicOrganize: Boolean
        get() = sp.getBoolean(KEY_ORGANIZE, true)
        set(value) = sp.edit().putBoolean(KEY_ORGANIZE, value).apply()

    var musicConcurrent: Int
        get() = sp.getInt(KEY_CONCURRENT, 1).coerceIn(1, 5)
        set(value) = sp.edit().putInt(KEY_CONCURRENT, value.coerceIn(1, 5)).apply()

    var musicNotifications: Boolean
        get() = sp.getBoolean(KEY_NOTIFICATIONS, true)
        set(value) = sp.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()

    var musicBackground: Boolean
        get() = sp.getBoolean(KEY_BACKGROUND, true)
        set(value) = sp.edit().putBoolean(KEY_BACKGROUND, value).apply()

    fun qualityPolicy(): QualityPolicy = QualityPolicy(
        flacFirst = musicFlacFirst,
        hiResFirst = musicHiResFirst,
        preferSpatial = musicPreferSpatial,
        showAll = musicShowAll,
        allowLossy = musicAllowLossy,
        losslessOnly = musicLosslessOnly
    )

    fun providerOrder(): List<String> {
        val value = musicProviderOrder.split(',').map { it.trim() }.filter { it.isNotBlank() }
        return if (value.isEmpty()) {
            com.andrespar2.pardl.core.music.MusicProviders.defaultOrder
        } else {
            value.distinct()
        }
    }

    fun providerEnabled(id: String, default: Boolean = true): Boolean =
        if (musicDisabledProviders.contains(id)) false else default

    companion object {
        private const val KEY_TREE_URI = "tree_uri"
        private const val KEY_FOLDER_NAME = "folder_name"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_GLASS_BLUR = "glass_blur"
        private const val KEY_ANIMATIONS = "animations_enabled"
        private const val KEY_MUSIC_PREFERRED = "music_preferred_provider"
        private const val KEY_MUSIC_ORDER = "music_provider_order"
        private const val KEY_MUSIC_DISABLED = "music_disabled_providers"
        private const val KEY_FLAC_FIRST = "music_flac_first"
        private const val KEY_HIRES_FIRST = "music_hires_first"
        private const val KEY_PREFER_SPATIAL = "music_prefer_spatial"
        private const val KEY_SHOW_ALL = "music_show_all"
        private const val KEY_ALLOW_LOSSY = "music_allow_lossy"
        private const val KEY_LOSSLESS_ONLY = "music_lossless_only"
        private const val KEY_ASK_ON_CHANGE = "music_ask_on_change"
        private const val KEY_EMBED_METADATA = "music_embed_metadata"
        private const val KEY_EMBED_COVER = "music_embed_cover"
        private const val KEY_EMBED_LYRICS = "music_embed_lyrics"
        private const val KEY_ORGANIZE = "music_organize"
        private const val KEY_CONCURRENT = "music_concurrent"
        private const val KEY_NOTIFICATIONS = "music_notifications"
        private const val KEY_BACKGROUND = "music_background"

        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context).also { instance = it }
            }
    }
}
