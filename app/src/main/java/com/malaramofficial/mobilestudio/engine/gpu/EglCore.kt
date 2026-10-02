package com.malaramofficial.mobilestudio.engine.gpu

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.util.Log
import android.view.Surface

/**
 * Manages EGL14 display, context, and surface configurations for hardware-accelerated
 * OpenGL ES 2.0/3.0 rendering. Decoupled from Android UI views.
 * Safe for headless test environments where EGL static singletons might be null.
 */
class EglCore(
    sharedContext: EGLContext? = null,
    flags: Int = 0
) {
    private var eglDisplay: EGLDisplay? = null
    var eglContext: EGLContext? = null
        private set
    private var eglConfig: EGLConfig? = null

    init {
        initEGL(sharedContext, flags)
    }

    private fun initEGL(sharedContext: EGLContext?, flags: Int) {
        try {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val display = eglDisplay
            if (display == null || display == EGL14.EGL_NO_DISPLAY) {
                Log.w(TAG, "unable to get EGL14 display (headless or unsupported)")
                return
            }

            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
                Log.w(TAG, "unable to initialize EGL14")
                return
            }

            val configAttribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGLExt.EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE
            )

            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(display, configAttribList, 0, configs, 0, configs.size, numConfigs, 0) || numConfigs[0] == 0) {
                val fallbackAttribList = intArrayOf(
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_NONE
                )
                EGL14.eglChooseConfig(display, fallbackAttribList, 0, configs, 0, configs.size, numConfigs, 0)
            }

            eglConfig = configs[0]

            val contextAttribList = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )

            val rootContext = sharedContext ?: EGL14.EGL_NO_CONTEXT
            eglContext = EGL14.eglCreateContext(display, eglConfig, rootContext, contextAttribList, 0)
            if (eglContext == null || eglContext == EGL14.EGL_NO_CONTEXT) {
                Log.w(TAG, "Failed to create EGLContext")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "EGL initialization failed on this device/runtime: ${t.message}")
        }
    }

    fun createWindowSurface(surface: Any): EGLSurface? {
        val display = eglDisplay ?: return null
        if (surface !is Surface && surface !is android.graphics.SurfaceTexture) {
            throw IllegalArgumentException("Invalid surface object: $surface")
        }
        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        val eglSurface = EGL14.eglCreateWindowSurface(display, eglConfig, surface, surfaceAttribs, 0)
        checkEglError("eglCreateWindowSurface")
        return eglSurface
    }

    fun createOffscreenSurface(width: Int, height: Int): EGLSurface? {
        val display = eglDisplay ?: return null
        val surfaceAttribs = intArrayOf(
            EGL14.EGL_WIDTH, width,
            EGL14.EGL_HEIGHT, height,
            EGL14.EGL_NONE
        )
        val eglSurface = EGL14.eglCreatePbufferSurface(display, eglConfig, surfaceAttribs, 0)
        checkEglError("eglCreatePbufferSurface")
        return eglSurface
    }

    fun makeCurrent(eglSurface: EGLSurface?) {
        val display = eglDisplay ?: return
        val context = eglContext ?: return
        if (eglSurface == null) return
        if (!EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) {
            Log.w(TAG, "eglMakeCurrent failed: " + EGL14.eglGetError())
        }
    }

    fun makeNothingCurrent() {
        val display = eglDisplay ?: return
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
    }

    fun swapBuffers(eglSurface: EGLSurface?): Boolean {
        val display = eglDisplay ?: return false
        if (eglSurface == null) return false
        return EGL14.eglSwapBuffers(display, eglSurface)
    }

    fun releaseSurface(eglSurface: EGLSurface?) {
        val display = eglDisplay ?: return
        if (eglSurface != null && eglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(display, eglSurface)
        }
    }

    fun release() {
        val display = eglDisplay
        if (display != null && display != EGL14.EGL_NO_DISPLAY) {
            makeNothingCurrent()
            val context = eglContext
            if (context != null && context != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(display, context)
                eglContext = null
            }
            EGL14.eglTerminate(display)
            eglDisplay = null
        }
    }

    private fun checkEglError(msg: String) {
        val error = EGL14.eglGetError()
        if (error != EGL14.EGL_SUCCESS) {
            Log.w(TAG, "$msg: EGL error: 0x" + Integer.toHexString(error))
        }
    }

    companion object {
        private const val TAG = "EglCore"
    }
}
