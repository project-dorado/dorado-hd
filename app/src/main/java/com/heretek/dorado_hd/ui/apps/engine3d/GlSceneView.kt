package com.heretek.dorado_hd.ui.apps.engine3d

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.IdentityHashMap
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Pure mark-and-sweep eviction policy for identity-keyed resource caches.
 *
 * Keys are [touch]ed while in use; after [maxIdleFrames] frames without a
 * touch they are reported by [evictable] and dropped by the owner. This
 * bounds the cache to the identities used by the current window of frames, so
 * an app that mints new [MeshData]/[TextureData] each frame cannot grow GPU
 * memory without limit. No Android types, so the policy is unit-testable.
 */
class FrameSweepCache(private val maxIdleFrames: Int = 2) {
    private val seen = IdentityHashMap<Any, Int>()
    private var frame = 0

    fun touch(key: Any) {
        seen[key] = frame
    }

    fun tick() {
        frame++
    }

    /** Drops and returns every key idle for more than [maxIdleFrames]. */
    fun evictable(): List<Any> {
        val cutoff = frame - maxIdleFrames
        val out = ArrayList<Any>()
        val iterator = seen.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value < cutoff) {
                out += entry.key
                iterator.remove()
            }
        }
        return out
    }

    fun size(): Int = seen.size

    fun clear() {
        seen.clear()
        frame = 0
    }
}

/**
 * Atomically rebuilds [Scene3d]: while [build] runs the GL thread's
 * synchronized snapshot blocks, so it can never sample a half-cleared scene.
 */
fun Scene3d.rebuild(build: Scene3d.() -> Unit) {
    synchronized(this) {
        clear()
        build()
    }
}

/** True while the hosting lifecycle is at least RESUMED (audio/physics gate). */
@Composable
fun rememberIsResumed(): Boolean {
    val owner = LocalLifecycleOwner.current
    var resumed by remember(owner) {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> resumed = false
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return resumed
}

/**
 * OpenGL ES 3.0 renderer for [Scene3d]. CPU-side meshes are uploaded lazily and
 * cached by instance; procedural textures are uploaded on first use and
 * re-created after context loss.
 *
 * This is deliberately a small, dependency-free engine: flat-shaded lit meshes,
 * one directional light, optional texture, linear fog, distance-independent
 * alpha. Every mesh and texture in Dorado-HD is authored in code.
 */
class GlSceneRenderer(private val scene: Scene3d) : GLSurfaceView.Renderer {

    private var program = 0
    private var uMvp = 0
    private var uModel = 0
    private var uNormalMat = 0
    private var uColor = 0
    private var uLightDir = 0
    private var uAmbient = 0
    private var uUseTexture = 0
    private var uUnlit = 0
    private var uEmissive = 0
    private var uFogDensity = 0
    private var uFogColor = 0
    private var uCameraPos = 0
    private var uTexture = 0

    private class UploadedMesh(
        val positions: FloatBuffer,
        val normals: FloatBuffer,
        val uvs: FloatBuffer,
        val indices: IntBuffer,
    )

    private val textureIds = IdentityHashMap<TextureData, Int>()
    private val buffers = IdentityHashMap<MeshData, UploadedMesh>()
    private val meshSweep = FrameSweepCache()
    private val textureSweep = FrameSweepCache()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        textureIds.clear()
        buffers.clear()
        meshSweep.clear()
        textureSweep.clear()
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        uMvp = GLES30.glGetUniformLocation(program, "uMvp")
        uModel = GLES30.glGetUniformLocation(program, "uModel")
        uNormalMat = GLES30.glGetUniformLocation(program, "uNormalMat")
        uColor = GLES30.glGetUniformLocation(program, "uColor")
        uLightDir = GLES30.glGetUniformLocation(program, "uLightDir")
        uAmbient = GLES30.glGetUniformLocation(program, "uAmbient")
        uUseTexture = GLES30.glGetUniformLocation(program, "uUseTexture")
        uUnlit = GLES30.glGetUniformLocation(program, "uUnlit")
        uEmissive = GLES30.glGetUniformLocation(program, "uEmissive")
        uFogDensity = GLES30.glGetUniformLocation(program, "uFogDensity")
        uFogColor = GLES30.glGetUniformLocation(program, "uFogColor")
        uCameraPos = GLES30.glGetUniformLocation(program, "uCameraPos")
        uTexture = GLES30.glGetUniformLocation(program, "uTexture")
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    /** Deletes GPU objects and clears all CPU caches (GL thread only). */
    fun release() {
        val ids = textureIds.values.toIntArray()
        if (ids.isNotEmpty()) GLES30.glDeleteTextures(ids.size, ids, 0)
        textureIds.clear()
        buffers.clear()
        meshSweep.clear()
        textureSweep.clear()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val bg = scene.backgroundColor
        GLES30.glClearColor(bg.r, bg.g, bg.b, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        if (program == 0) return
        GLES30.glUseProgram(program)

        val viewport = IntArray(4)
        GLES30.glGetIntegerv(GLES30.GL_VIEWPORT, viewport, 0)
        val aspect = if (viewport[3] == 0) 1f else viewport[2].toFloat() / viewport[3]
        val camera = scene.camera
        val view = camera.view()
        val projection = camera.projection(aspect)
        val viewProjection = projection * view
        val light = scene.lightDirection.normalized()

        GLES30.glUniform3f(uLightDir, light.x, light.y, light.z)
        GLES30.glUniform1f(uAmbient, scene.ambient)
        GLES30.glUniform3f(uFogColor, bg.r, bg.g, bg.b)
        GLES30.glUniform3f(uCameraPos, camera.eye.x, camera.eye.y, camera.eye.z)

        for (node in scene.snapshotNodes()) {
            if (!node.visible) continue
            val material = node.material
            val mesh = node.mesh
            val uploaded = uploadMesh(mesh)
            meshSweep.touch(mesh)
            val world = node.transform
            val mvp = viewProjection * world
            GLES30.glUniformMatrix4fv(uMvp, 1, false, mvp.m, 0)
            GLES30.glUniformMatrix4fv(uModel, 1, false, world.m, 0)
            GLES30.glUniformMatrix4fv(uNormalMat, 1, false, Mat4.normalMatrix(world).m, 0)
            GLES30.glUniform4f(uColor, material.color.r, material.color.g, material.color.b, material.color.a)
            GLES30.glUniform1f(uUnlit, if (material.unlit) 1f else 0f)
            GLES30.glUniform1f(uEmissive, material.emissive)
            GLES30.glUniform1f(uFogDensity, scene.fogDensity)
            val texture = material.texture
            if (texture != null) {
                textureSweep.touch(texture)
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, uploadTexture(texture))
                GLES30.glUniform1i(uTexture, 0)
                GLES30.glUniform1f(uUseTexture, 1f)
            } else {
                GLES30.glUniform1f(uUseTexture, 0f)
            }
            GLES30.glBindVertexArray(0)
            val stride = 0
            // positions (location 0)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, uploaded.positions)
            // normals (location 1)
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, uploaded.normals)
            // uvs (location 2)
            GLES30.glEnableVertexAttribArray(2)
            GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, stride, uploaded.uvs)
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, uploaded.indices.capacity(), GLES30.GL_UNSIGNED_INT, uploaded.indices)
            GLES30.glDisableVertexAttribArray(0)
            GLES30.glDisableVertexAttribArray(1)
            GLES30.glDisableVertexAttribArray(2)
        }
        sweepCaches()
    }

    /**
     * Evicts mesh/texture identities not seen for a few frames, deleting their
     * GPU objects. Bounded caches are what keep per-frame scene identities from
     * leaking GPU memory forever.
     */
    private fun sweepCaches() {
        meshSweep.tick()
        for (key in meshSweep.evictable()) {
            buffers.remove(key as MeshData)
        }
        textureSweep.tick()
        for (key in textureSweep.evictable()) {
            textureIds.remove(key as TextureData)?.let { id ->
                GLES30.glDeleteTextures(1, intArrayOf(id), 0)
            }
        }
    }

    private fun uploadMesh(mesh: MeshData): UploadedMesh =
        buffers.getOrPut(mesh) {
            UploadedMesh(
                makeFloatBuffer(mesh.positions),
                makeFloatBuffer(mesh.normals),
                makeFloatBuffer(mesh.uvs),
                makeIntBuffer(mesh.indices),
            )
        }

    private fun uploadTexture(data: TextureData): Int {
        textureIds[data]?.let { return it }
        val bitmap = Bitmap.createBitmap(data.width, data.height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(data.pixels, 0, data.width, 0, 0, data.width, data.height)
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
        android.opengl.GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()
        textureIds[data] = ids[0]
        return ids[0]
    }

    private fun makeFloatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().put(values).also { it.position(0) }

    private fun makeIntBuffer(values: IntArray): IntBuffer =
        ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder())
            .asIntBuffer().put(values).also { it.position(0) }

    private fun buildProgram(vertex: String, fragment: String): Int {
        val vs = compile(GLES30.GL_VERTEX_SHADER, vertex)
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, fragment)
        val prog = GLES30.glCreateProgram()
        GLES30.glAttachShader(prog, vs)
        GLES30.glAttachShader(prog, fs)
        GLES30.glLinkProgram(prog)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        return prog
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        return shader
    }

    companion object {
        private const val VERTEX_SHADER = """
            #version 300 es
            in vec3 aPos;
            in vec3 aNormal;
            in vec2 aUv;
            uniform mat4 uMvp;
            uniform mat4 uModel;
            uniform mat4 uNormalMat;
            uniform vec3 uCameraPos;
            out vec3 vNormal;
            out vec2 vUv;
            out float vFogDepth;
            void main() {
                vec4 world = uModel * vec4(aPos, 1.0);
                gl_Position = uMvp * vec4(aPos, 1.0);
                vNormal = mat3(uNormalMat) * aNormal;
                vUv = aUv;
                vFogDepth = length(world.xyz - uCameraPos);
            }
        """

        private const val FRAGMENT_SHADER = """
            #version 300 es
            precision mediump float;
            in vec3 vNormal;
            in vec2 vUv;
            in float vFogDepth;
            uniform vec4 uColor;
            uniform vec3 uLightDir;
            uniform float uAmbient;
            uniform sampler2D uTexture;
            uniform float uUseTexture;
            uniform float uUnlit;
            uniform float uEmissive;
            uniform float uFogDensity;
            uniform vec3 uFogColor;
            out vec4 outColor;
            void main() {
                vec4 base = uColor;
                if (uUseTexture > 0.5) base *= texture(uTexture, vUv);
                float light = 1.0;
                if (uUnlit < 0.5) {
                    float d = max(dot(normalize(vNormal), normalize(-uLightDir)), 0.0);
                    light = uAmbient + (1.0 - uAmbient) * d + uEmissive;
                }
                vec3 rgb = base.rgb * clamp(light, 0.0, 2.0);
                if (uFogDensity > 0.0) {
                    float f = clamp(exp(-uFogDensity * vFogDepth), 0.0, 1.0);
                    rgb = mix(uFogColor, rgb, f);
                }
                outColor = vec4(rgb, base.a);
            }
        """
    }
}

/**
 * Compose host for [scene]. Taps are converted to a world ray and resolved by
 * [Scene3d.pick]; [onPick] receives the hit node tag (or null).
 */
@Composable
fun Scene3dView(
    scene: Scene3d,
    modifier: Modifier = Modifier,
    onPick: ((String?) -> Unit)? = null,
) {
    val context: Context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val renderer = remember(scene) { GlSceneRenderer(scene) }
    val glView = remember(scene, renderer) {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(3)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            if (onPick != null) {
                setOnTouchListener { view, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        val ndcX = (event.x / view.width) * 2f - 1f
                        val ndcY = 1f - (event.y / view.height) * 2f
                        val aspect = if (view.height == 0) 1f else view.width.toFloat() / view.height
                        val ray = scene.camera.ray(ndcX, ndcY, aspect)
                        onPick(scene.pick(ray))
                    }
                    true
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, glView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glView.onResume()
                Lifecycle.Event.ON_PAUSE -> glView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glView.onPause()
            glView.queueEvent { renderer.release() }
        }
    }

    Box(modifier) {
        AndroidView(
            factory = { glView },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
