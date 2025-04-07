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

    override fun onSurfaceCreated(gl: GL10, config: javax.microedition.khronos.egl.EGLConfig) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)

        val fragShaderCode = source

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, DEFAULT_VERTEX_SHADER)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragShaderCode)

        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }

        uTime = GLES20.glGetUniformLocation(program, "u_time")
        uResolution = GLES20.glGetUniformLocation(program, "u_resolution")

        val bb = ByteBuffer.allocateDirect(squareCoords.size * 4)
        bb.order(ByteOrder.nativeOrder())
        squareCoords.forEach { bb.putFloat(it) }
        bb.position(0)
        vertexBuffer = bb.asFloatBuffer()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(program)

        val time = (SystemClock.elapsedRealtime() - startTime) / 1000f
        GLES20.glUniform1f(uTime, time)
        GLES20.glUniform2f(uResolution, 512f, 512f) // TODO: Use view size

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
            void main() {
                gl_Position = vPosition;
            }
        """
    }
}

class ShaderSurfaceView(context: Context, shaderSource: String) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(2)
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
