package com.andrespar2.pardl

import android.app.Application
import com.andrespar2.pardl.core.Engine

class ParDLApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // La inicialización real corre en segundo plano y no bloquea el arranque.
        Engine.ensureStarted(this)
    }
}
