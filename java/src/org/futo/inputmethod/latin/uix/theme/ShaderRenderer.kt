package org.futo.inputmethod.latin.uix.theme

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.SystemClock
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
import javax.microedition.khronos.opengles.GL10


class ShaderRenderer(private val context: Context, private val source: String) : GLSurfaceView.Renderer {
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

    val renderRes = 256 to 256
    private lateinit var fboTexture: IntArray
    private lateinit var fbo: IntArray

    override fun onSurfaceCreated(gl: GL10, config: javax.microedition.khronos.egl.EGLConfig) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)

        val fragShaderCode = source

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

        uTime = GLES20.glGetUniformLocation(program, "u_time")
        uResolution = GLES20.glGetUniformLocation(program, "u_resolution")

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
    }

    var surfaceSize = 0 to 0
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceSize = width to height
    }

    override fun onDrawFrame(gl: GL10?) {
        run {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0])
            GLES20.glViewport(0, 0, renderRes.first, renderRes.second)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glUseProgram(program)
            val time = ((SystemClock.elapsedRealtime() - startTime) % (120 * 1000)) / 1000f
            GLES20.glUniform1f(uTime, time)
            GLES20.glUniform2f(uResolution, renderRes.first.toFloat(), renderRes.second.toFloat())

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

        run {
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
        return GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, shaderCode)
            GLES20.glCompileShader(it)
        }
    }

    companion object {
        private const val DEFAULT_VERTEX_SHADER = """
            attribute vec4 vPosition;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = vPosition;
                vTexCoord = vec2((vPosition.xy + 1.0) * 0.5);
            }
        """

        private const val COPY_FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            varying vec2 vTexCoord;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """
    }
}

class ShaderSurfaceView(context: Context, shaderSource: String) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(2)
        //debugFlags = DEBUG_LOG_GL_CALLS or DEBUG_CHECK_GL_ERROR
        setRenderer(ShaderRenderer(context, shaderSource))
        renderMode = RENDERMODE_WHEN_DIRTY
    }
}

@Composable
fun KeyboardSurfaceShaderBackground(shaderSource: String, modifier: Modifier = Modifier) {
    val view: MutableState<GLSurfaceView?> = remember { mutableStateOf<GLSurfaceView?>(null) }
    LaunchedEffect(view.value) {
        while (true) {
            delay(16L)
            view.value?.requestRender()
        }
    }

    AndroidView(
        factory = { context -> ShaderSurfaceView(context, shaderSource).also { view.value = it } },
        modifier = modifier
    )
}
