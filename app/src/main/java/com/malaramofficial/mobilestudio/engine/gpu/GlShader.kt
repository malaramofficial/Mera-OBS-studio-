package com.malaramofficial.mobilestudio.engine.gpu

import android.opengl.GLES20
import android.util.Log

/**
 * OpenGL ES shader compiler and program manager for GPU compositor layer rendering.
 */
class GlShader(
    val vertexShaderCode: String,
    val fragmentShaderCode: String
) {
    var programHandle: Int = 0
        private set

    init {
        compile()
    }

    private fun compile() {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        if (vertexShader == 0 || fragmentShader == 0) {
            Log.e(TAG, "Shader compilation failed")
            return
        }

        programHandle = GLES20.glCreateProgram()
        if (programHandle != 0) {
            GLES20.glAttachShader(programHandle, vertexShader)
            GLES20.glAttachShader(programHandle, fragmentShader)
            GLES20.glLinkProgram(programHandle)

            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(programHandle, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "Could not link program: " + GLES20.glGetProgramInfoLog(programHandle))
                GLES20.glDeleteProgram(programHandle)
                programHandle = 0
            }
        }
        // Cleanup shader objects after linking
        if (vertexShader != 0) GLES20.glDeleteShader(vertexShader)
        if (fragmentShader != 0) GLES20.glDeleteShader(fragmentShader)
    }

    fun use() {
        if (programHandle != 0) {
            GLES20.glUseProgram(programHandle)
        }
    }

    fun release() {
        if (programHandle != 0) {
            GLES20.glDeleteProgram(programHandle)
            programHandle = 0
        }
    }

    companion object {
        private const val TAG = "GlShader"

        fun loadShader(shaderType: Int, source: String): Int {
            var shader = GLES20.glCreateShader(shaderType)
            if (shader != 0) {
                GLES20.glShaderSource(shader, source)
                GLES20.glCompileShader(shader)
                val compiled = IntArray(1)
                GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
                if (compiled[0] == 0) {
                    Log.e(TAG, "Could not compile shader $shaderType: " + GLES20.glGetShaderInfoLog(shader))
                    GLES20.glDeleteShader(shader)
                    shader = 0
                }
            }
            return shader
        }

        val VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uTexMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTextureCoord = (uTexMatrix * aTextureCoord).xy;
            }
        """.trimIndent()

        /**
         * Fragment shader for hardware camera frames delivered via GL_TEXTURE_EXTERNAL_OES.
         * Includes real-time crop bounds checking, layer opacity blending, and Chroma Key processing.
         */
        val FRAGMENT_SHADER_OES = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES uTexture;
            uniform float uOpacity;
            uniform vec4 uCrop; // x=left, y=top, z=right, w=bottom in normalized 0.0-1.0
            uniform int uChromaEnabled;
            uniform vec3 uChromaKeyColor; // RGB normalized [0..1]
            uniform float uChromaSimilarity;
            uniform float uChromaSmoothness;
            uniform float uChromaSpill;

            void main() {
                // Apply Crop boundaries (left, top, right, bottom)
                if (vTextureCoord.x < uCrop.x || vTextureCoord.x > (1.0 - uCrop.z) ||
                    vTextureCoord.y < uCrop.y || vTextureCoord.y > (1.0 - uCrop.w)) {
                    discard;
                }

                vec4 color = texture2D(uTexture, vTextureCoord);

                // Chroma Key Processing
                if (uChromaEnabled == 1) {
                    float diff = length(color.rgb - uChromaKeyColor);
                    float edge0 = uChromaSimilarity;
                    float edge1 = uChromaSimilarity + uChromaSmoothness;
                    float alpha = smoothstep(edge0, edge1, diff);

                    // Spill reduction
                    if (diff < edge1 && uChromaSpill > 0.0) {
                        float spillVal = max(color.g - max(color.r, color.b), 0.0);
                        color.g -= spillVal * uChromaSpill;
                    }
                    color.a *= alpha;
                }

                color.a *= uOpacity;
                gl_FragColor = color;
            }
        """.trimIndent()

        /**
         * Fragment shader for standard GL_TEXTURE_2D textures (e.g. Image, Text overlays).
         */
        val FRAGMENT_SHADER_2D = """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D uTexture;
            uniform float uOpacity;
            uniform vec4 uCrop;

            void main() {
                if (vTextureCoord.x < uCrop.x || vTextureCoord.x > (1.0 - uCrop.z) ||
                    vTextureCoord.y < uCrop.y || vTextureCoord.y > (1.0 - uCrop.w)) {
                    discard;
                }

                vec4 color = texture2D(uTexture, vTextureCoord);
                color.a *= uOpacity;
                gl_FragColor = color;
            }
        """.trimIndent()
    }
}
