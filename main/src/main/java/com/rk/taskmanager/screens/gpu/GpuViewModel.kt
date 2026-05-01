package com.rk.taskmanager.screens.gpu

import android.app.Application
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class GpuFullInfo(
    val renderer: String?,
    val vendor: String?,
    val openGlVersion: String?,
    val glslVersion: String?,
    val extensions: String?,
    val maxTextureSize: Int,
    val maxCubeMapSize: Int,
    val maxVertexAttribs: Int,
    val maxTextureUnits: Int,
    val maxRenderbufferSize: Int,
    val maxViewportWidth: Int,
    val maxViewportHeight: Int,
    val vulkanSupported: Boolean,
    val vulkanHardwareLevel: Int?,
    val vulkanApiVersion: String?
)

class GpuViewModel(application: Application) : AndroidViewModel(application) {

    private val _gpuInfo = MutableStateFlow<GpuFullInfo?>(null)
    val gpuInfo: StateFlow<GpuFullInfo?> = _gpuInfo


    init {
        viewModelScope.launch(Dispatchers.Default) {
            _gpuInfo.value = loadGpuInfo()
        }
    }

    private fun loadGpuInfo(): GpuFullInfo? {
        return runCatching {

            // ----------- EGL CONTEXT -----------
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null

            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
            )

            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)

            EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0)
            if (numConfigs[0] <= 0 || configs[0] == null) return null

            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, // Try ES3
                EGL14.EGL_NONE
            )

            val context = EGL14.eglCreateContext(
                display,
                configs[0],
                EGL14.EGL_NO_CONTEXT,
                contextAttribs,
                0
            )

            val surfaceAttribs = intArrayOf(
                EGL14.EGL_WIDTH, 1,
                EGL14.EGL_HEIGHT, 1,
                EGL14.EGL_NONE
            )

            val surface = EGL14.eglCreatePbufferSurface(
                display,
                configs[0],
                surfaceAttribs,
                0
            )

            EGL14.eglMakeCurrent(display, surface, surface, context)

            fun getInt(param: Int): Int {
                val arr = IntArray(1)
                GLES20.glGetIntegerv(param, arr, 0)
                return arr[0]
            }

            val viewport = IntArray(2)
            GLES20.glGetIntegerv(GLES20.GL_MAX_VIEWPORT_DIMS, viewport, 0)

            // ----------- OPENGL INFO -----------
            val renderer = GLES20.glGetString(GLES20.GL_RENDERER)
            val vendor = GLES20.glGetString(GLES20.GL_VENDOR)
            val glVersion = GLES20.glGetString(GLES20.GL_VERSION)
            val glslVersion = GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION)
            val extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS)

            val maxTextureSize = getInt(GLES20.GL_MAX_TEXTURE_SIZE)
            val maxCubeMapSize = getInt(GLES20.GL_MAX_CUBE_MAP_TEXTURE_SIZE)
            val maxVertexAttribs = getInt(GLES20.GL_MAX_VERTEX_ATTRIBS)
            val maxTextureUnits = getInt(GLES20.GL_MAX_TEXTURE_IMAGE_UNITS)
            val maxRenderbufferSize = getInt(GLES20.GL_MAX_RENDERBUFFER_SIZE)

            // Cleanup
            EGL14.eglMakeCurrent(display,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)

            // ----------- VULKAN INFO -----------
            val contextApp = getApplication<Application>()
            val pm = contextApp.packageManager

            val vulkanSupported =
                pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)

            var vulkanLevel: Int? = null
            var vulkanVersionStr: String? = null

            pm.systemAvailableFeatures.forEach {
                when (it.name) {
                    PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL -> {
                        vulkanLevel = it.version
                    }

                    PackageManager.FEATURE_VULKAN_HARDWARE_VERSION -> {
                        val version = it.version
                        val major = version shr 22
                        val minor = (version shr 12) and 0x3FF
                        val patch = version and 0xFFF
                        vulkanVersionStr = "$major.$minor.$patch"
                    }
                }
            }

            GpuFullInfo(
                renderer,
                vendor,
                glVersion,
                glslVersion,
                extensions,
                maxTextureSize,
                maxCubeMapSize,
                maxVertexAttribs,
                maxTextureUnits,
                maxRenderbufferSize,
                viewport[0],
                viewport[1],
                vulkanSupported,
                vulkanLevel,
                vulkanVersionStr
            )

        }.getOrNull()
    }
}