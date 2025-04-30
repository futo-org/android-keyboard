package org.futo.inputmethod.latin.uix.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.EGL14
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.opengles.GL10
import androidx.core.graphics.createBitmap

fun loadBitmapFromAssets(context: Context, fileName: String): Bitmap {
    context.assets.open(fileName).use { inputStream ->
        return BitmapFactory.decodeStream(inputStream)
    }
}

class ShaderRenderer(
    private val context: Context,
    private val source: String,
    private val iChannels: List<Bitmap>,
    private val renderRes: Pair<Int, Int> = 256 to 256,
    private val useRescaling: Boolean = true
) : GLSurfaceView.Renderer {
    private var program = 0
    private var fullscreenProgram = 0
    private var startTime = SystemClock.elapsedRealtime()
    private var uTime = 0
    private var uResolution = 0
    private lateinit var vertexBuffer: FloatBuffer

    private val squareCoords = floatArrayOf(
        -1f,  1f,
        -1f, -1f,
        1f,  1f,
        1f, -1f
    )

    private lateinit var fboTexture: IntArray
    private lateinit var fbo: IntArray

    private var iTextures = listOf<Int>()
    private fun createTextureFromBitmap(bitmap: Bitmap): Int {
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        val textureId = textureIds[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

        // Texture settings
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)

        // Load bitmap into texture
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        return textureId
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)

        val fragShaderCode = StringBuilder().let { builder ->
            builder.append("""#version 320 es
                precision highp float;
                uniform float iTime;
                uniform vec3 iResolution;
                out vec4 fragColor;
            """.trimIndent())

            builder.append('\n')

            iChannels.forEachIndexed { i, _ ->
                builder.append("uniform sampler2D iChannel$i;\n")
            }

            builder.append('\n')

            builder.append(source)
            builder.append('\n')

            builder.append("""
                void main(void) {
                    vec2 fragCoord = gl_FragCoord.xy;
                    fragCoord.y = iResolution.y - fragCoord.y - 1.;
                    mainImage(fragColor, fragCoord);
                }
            """.trimIndent())

            builder.toString()
        }

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, DEFAULT_VERTEX_SHADER)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragShaderCode)
        val fullscreenFragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, COPY_FRAGMENT_SHADER)

        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }

        fullscreenProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fullscreenFragmentShader)
            GLES20.glLinkProgram(it)
        }

        uTime = GLES20.glGetUniformLocation(program, "iTime")
        uResolution = GLES20.glGetUniformLocation(program, "iResolution")

        val bb = ByteBuffer.allocateDirect(squareCoords.size * 4)
        bb.order(ByteOrder.nativeOrder())
        squareCoords.forEach { bb.putFloat(it) }
        bb.position(0)
        vertexBuffer = bb.asFloatBuffer()


        // Create a texture for the FBO
        fboTexture = IntArray(1)
        GLES20.glGenTextures(1, fboTexture, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTexture[0])
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0, GLES20.GL_RGBA,
            renderRes.first, renderRes.second,
            0, GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            null
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        fbo = IntArray(1)
        GLES20.glGenFramebuffers(1, fbo, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0])
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboTexture[0], 0)
        if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw RuntimeException("Framebuffer not complete!")
        }

        iTextures = iChannels.map { createTextureFromBitmap(it) }
    }

    var surfaceSize = 0 to 0
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceSize = width to height
    }

    override fun onDrawFrame(gl: GL10?) {
        run {
            val viewportSize: Pair<Int, Int>
            if(useRescaling) {
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0])
                viewportSize = renderRes
            } else {
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
                viewportSize = surfaceSize
            }

            GLES20.glViewport(0, 0, viewportSize.first, viewportSize.second)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glUseProgram(program)
            val time = ((SystemClock.elapsedRealtime() - startTime) % (120 * 1000)) / 1000f
            GLES20.glUniform1f(uTime, time)

            // The third component is meant to be pixel aspect ratio
            // Just passing 1.0 for now
            GLES20.glUniform3f(uResolution, viewportSize.first.toFloat(), viewportSize.second.toFloat(), 1.0f)

            val textureUnits = listOf(
                GLES20.GL_TEXTURE0,
                GLES20.GL_TEXTURE1,
                GLES20.GL_TEXTURE2,
                GLES20.GL_TEXTURE3,
                GLES20.GL_TEXTURE4,
                GLES20.GL_TEXTURE5,
                GLES20.GL_TEXTURE6
            )

            iTextures.forEachIndexed { i, v ->
                // Bind texture to texture unit i
                GLES20.glActiveTexture(textureUnits[i])
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, v)

                // Set sampler2D uniform to use texture unit i
                val textureUniform = GLES20.glGetUniformLocation(program, "iChannel$i")
                GLES20.glUniform1i(textureUniform, i)
            }

            val positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
            GLES20.glEnableVertexAttribArray(positionHandle)

            GLES20.glVertexAttribPointer(
                positionHandle,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                vertexBuffer
            )

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        }

        if(useRescaling) {
            GLES20.glViewport(0, 0, surfaceSize.first, surfaceSize.second)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(fullscreenProgram)
            val positionHandle_2 = GLES20.glGetAttribLocation(fullscreenProgram, "vPosition")
            val textureHandle_2 = GLES20.glGetUniformLocation(fullscreenProgram, "uTexture")

            GLES20.glEnableVertexAttribArray(positionHandle_2)
            GLES20.glVertexAttribPointer(
                positionHandle_2,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                vertexBuffer
            )

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTexture[0])
            GLES20.glUniform1i(textureHandle_2, 0)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(positionHandle_2)
        }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)

        if (compileStatus[0] == 0) {
            val errorLog = GLES20.glGetShaderInfoLog(shader)
            Log.e("ShaderCompile", "Shader compilation failed:\n$errorLog\nSource:\n$shaderCode")
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Shader compilation failed.")
        }

        val infoLog = GLES20.glGetShaderInfoLog(shader)
        if (infoLog.isNotBlank()) {
            Log.d("ShaderCompile", "Shader compile log:\n$infoLog")
        }

        return shader
    }

    companion object {
        private const val DEFAULT_VERTEX_SHADER = """#version 320 es
            precision highp float;
            in vec4 vPosition;
            out vec2 vTexCoord;
            void main() {
                gl_Position = vPosition;
                vTexCoord = vec2((vPosition.xy + 1.0) * 0.5);
            }
        """

        private const val COPY_FRAGMENT_SHADER = """#version 320 es
            precision highp float;
            uniform sampler2D uTexture;
            in vec2 vTexCoord;
            out vec4 fragColor;
            void main() {
                fragColor = texture(uTexture, vTexCoord);
            }
        """
    }
}

class ShaderSurfaceView(context: Context, shaderSource: String, iChannels: List<Bitmap>) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(2)
        //debugFlags = DEBUG_LOG_GL_CALLS or DEBUG_CHECK_GL_ERROR
        setRenderer(ShaderRenderer(context, shaderSource, iChannels))
        renderMode = RENDERMODE_WHEN_DIRTY
    }
}

@Composable
fun KeyboardSurfaceShaderBackground(shaderSource: String, iChannels: List<Bitmap> = listOf(), modifier: Modifier = Modifier) {
    val view: MutableState<GLSurfaceView?> = remember { mutableStateOf<GLSurfaceView?>(null) }
    LaunchedEffect(view.value) {
        while (true) {
            delay(16L)
            view.value?.requestRender()
        }
    }

    AndroidView(
        factory = { context -> ShaderSurfaceView(context, shaderSource, iChannels).also { view.value = it } },
        modifier = modifier
    )
}


fun renderShaderToBitmap(context: Context, shaderSource: String, width: Int, height: Int, iChannels: List<Bitmap>): Bitmap {
    val egl = (EGLContext.getEGL() as EGL10)
    val display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
    egl.eglInitialize(display, null)

    val attribList = intArrayOf(
        EGL10.EGL_RED_SIZE, 8,
        EGL10.EGL_GREEN_SIZE, 8,
        EGL10.EGL_BLUE_SIZE, 8,
        EGL10.EGL_RENDERABLE_TYPE, 4,  // EGL_OPENGL_ES2_BIT
        EGL10.EGL_NONE
    )

    val configs = arrayOfNulls<EGLConfig>(1)
    val numConfigs = IntArray(1)
    egl.eglChooseConfig(display, attribList, configs, 1, numConfigs)

    val config = configs[0]

    val attrib_list = intArrayOf(
        EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
        EGL10.EGL_NONE
    )
    val eglContext = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, attrib_list)

    val surfaceAttribs = intArrayOf(EGL10.EGL_WIDTH, width, EGL10.EGL_HEIGHT, height, EGL10.EGL_NONE)
    val eglSurface = egl.eglCreatePbufferSurface(display, config, surfaceAttribs)

    egl.eglMakeCurrent(display, eglSurface, eglSurface, eglContext)

    val renderer = ShaderRenderer(context, shaderSource, iChannels, width to height, false)
    renderer.onSurfaceCreated(null, config!!)
    renderer.onSurfaceChanged(null, width, height)
    renderer.onDrawFrame(null)

    val buffer = ByteBuffer.allocateDirect(width * height * 4)
    GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)

    val bitmap = createBitmap(width, height)
    buffer.rewind()
    bitmap.copyPixelsFromBuffer(buffer)

    egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT)
    egl.eglDestroySurface(display, eglSurface)
    egl.eglDestroyContext(display, eglContext)
    egl.eglTerminate(display)

    return bitmap
}
