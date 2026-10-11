package com.andrespar2.pardl.core

import android.content.Context
import android.content.SharedPreferences

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

    companion object {
        private const val KEY_TREE_URI = "tree_uri"
        private const val KEY_FOLDER_NAME = "folder_name"

        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context).also { instance = it }
            }
    }
}
