package com.andrespar2.pardl.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.andrespar2.pardl.core.Prefs

/** Estado global de apariencia para que Ajustes actualice toda la app al instante. */
object Appearance {

    private val _mode = MutableStateFlow("dark")
    val mode: StateFlow<String> = _mode.asStateFlow()

    private val _blur = MutableStateFlow(0.65f)
    val blur: StateFlow<Float> = _blur.asStateFlow()

    private val _animations = MutableStateFlow(true)
    val animations: StateFlow<Boolean> = _animations.asStateFlow()

    fun initialize(context: Context) {
        val prefs = Prefs.get(context)
        _mode.value = prefs.themeMode
        _blur.value = prefs.glassBlur
        _animations.value = prefs.animationsEnabled
    }

    fun setMode(context: Context, value: String) {
        Prefs.get(context).themeMode = value
        _mode.value = value
    }

    fun setBlur(context: Context, value: Float) {
        Prefs.get(context).glassBlur = value
        _blur.value = value
    }

    fun setAnimations(context: Context, value: Boolean) {
        Prefs.get(context).animationsEnabled = value
        _animations.value = value
    }
}
