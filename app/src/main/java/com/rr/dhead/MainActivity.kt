package com.rr.dhead

// Android OpenGL and UI imports
import android.opengl.GLSurfaceView
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Bundle

// Jetpack Compose and Activity imports
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext

// Project imports
import com.rr.dhead.ui.theme.DheadTheme

// Coroutines for animation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// OpenGL and rendering imports
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sin

/**
 * Main Activity that hosts the 3D head animation app.
 * 
 * This app demonstrates:
 * - Loading and displaying 3D GLB models using OpenGL ES
 * - Implementing shape key (morph target) animation
 * - Jetpack Compose UI integration with OpenGL
 * - Coroutine-based animation loops
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Modern Android edge-to-edge display
        
        setContent {
            DheadTheme {
                // Scaffold provides basic Material Design layout structure
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HeadAnimationScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

/**
 * Main composable screen that displays the 3D head model with animation controls.
 * 
 * Key Compose concepts demonstrated:
 * - State management with remember and mutableStateOf
 * - Side effects with LaunchedEffect for animations
 * - Integrating native OpenGL views with AndroidView
 * - UI layouts with Box, Card, Column, and Row
 */
@Composable
fun HeadAnimationScreen(modifier: Modifier = Modifier) {
    // STATE MANAGEMENT
    // Remember state across recompositions - these persist when UI updates
    var shapeKeyValue by remember { mutableFloatStateOf(0f) }     // Current shape key value (0.0 to 1.0)
    var autoAnimate by remember { mutableStateOf(false) }         // Whether auto-animation is enabled
    
    // Get Android context for OpenGL setup
    val context = LocalContext.current
    // Remember the renderer instance - expensive to recreate
    val renderer = remember { HeadRenderer(context) }
    
    // ANIMATION LOGIC
    // LaunchedEffect runs side effects in a coroutine when the key (autoAnimate) changes
    // This is the proper Compose way to handle continuous animations
    LaunchedEffect(autoAnimate) {
        if (autoAnimate) {
            val startTime = System.currentTimeMillis()
            
            // Animation loop - runs at ~60fps while autoAnimate is true
            while (isActive) { // isActive checks if the coroutine is still active
                // Double-check autoAnimate state on each frame for immediate response
                if (!autoAnimate) break
                
                // Calculate elapsed time and create smooth sine wave animation
                val elapsed = (System.currentTimeMillis() - startTime) * 0.002f  // Slow animation speed
                val newValue = (sin(elapsed) + 1f) / 2f                          // Map sin(-1,1) to (0,1)
                
                // Update both UI state and OpenGL renderer
                shapeKeyValue = newValue
                renderer.setShapeKeyValue(newValue)
                
                delay(16) // ~60fps (16ms per frame)
            }
        }
    }
    
    // UI LAYOUT
    // Box allows overlapping children - 3D view as background, controls as overlay
    Box(modifier = modifier.fillMaxSize()) {
        
        // OPENGL 3D VIEW
        // AndroidView integrates native Android views into Compose
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                GLSurfaceView(context).apply {
                    setEGLContextClientVersion(2)        // Use OpenGL ES 2.0
                    setRenderer(renderer)                // Our custom OpenGL renderer
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY  // Continuous rendering
                }
            }
        )
        
        // CONTROL PANEL OVERLAY
        // Card provides Material Design elevated surface with rounded corners
        Card(
            modifier = Modifier
                .align(Alignment.TopStart)           // Position at top-left
                .padding(16.dp),                     // Margin from screen edge
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.7f)  // Semi-transparent black
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)  // Space between children
            ) {
                // Title
                Text(
                    text = "KeyOne Shape Key",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
                
                // SLIDER CONTROL
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Slider(
                        value = shapeKeyValue,           // Current value to display
                        onValueChange = { value ->       // Called when user drags slider
                            if (!autoAnimate) {          // Only allow manual control when not auto-animating
                                shapeKeyValue = value
                                renderer.setShapeKeyValue(value)  // Update 3D model immediately
                            }
                        },
                        modifier = Modifier.width(200.dp),
                        enabled = !autoAnimate           // Disable slider during auto-animation
                    )
                    // Value display
                    Text(
                        text = "%.2f".format(shapeKeyValue),  // Show current value with 2 decimal places
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                // AUTO-ANIMATE CHECKBOX
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = autoAnimate,
                        onCheckedChange = { autoAnimate = it }  // Toggle auto-animation on/off
                    )
                    Text(
                        text = "Auto Animate",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/**
 * OpenGL ES Renderer for 3D head model with shape key animation.
 * 
 * This class demonstrates:
 * - OpenGL ES 2.0 shader programming
 * - Vertex buffer management and morphing
 * - 3D matrix transformations (MVP matrix)
 * - Basic lighting calculations
 * - Thread-safe state updates from UI thread
 */
class HeadRenderer(private val context: android.content.Context) : GLSurfaceView.Renderer {
    
    // SHADER PROGRAMS
    // Vertex shader: processes each vertex, calculates position and lighting
    private val vertexShaderCode = """
        attribute vec4 vPosition;        // Vertex position from vertex buffer
        attribute vec3 vNormal;          // Vertex normal for lighting
        
        uniform mat4 uMVPMatrix;         // Model-View-Projection matrix
        uniform mat4 uModelMatrix;       // Model transformation matrix
        uniform mat4 uNormalMatrix;      // Normal transformation matrix
        uniform vec3 uLightPos;          // Light position in world space
        
        varying float vLightIntensity;   // Pass lighting to fragment shader
        
        void main() {
            // Transform vertex to clip space
            gl_Position = uMVPMatrix * vPosition;
            
            // Calculate basic lighting (Lambertian reflectance)
            vec4 worldPos = uModelMatrix * vPosition;
            vec3 worldNormal = normalize((uNormalMatrix * vec4(vNormal, 0.0)).xyz);
            vec3 lightDir = normalize(uLightPos - worldPos.xyz);
            vLightIntensity = max(dot(worldNormal, lightDir), 0.2);  // Min 0.2 for ambient
        }
    """.trimIndent()
    
    // Fragment shader: determines final pixel color
    private val fragmentShaderCode = """
        precision mediump float;           // Use medium precision for performance
        uniform vec4 vColor;               // Base material color
        varying float vLightIntensity;     // Lighting value from vertex shader
        
        void main() {
            // Final color = base color modulated by lighting
            gl_FragColor = vec4(vColor.rgb * vLightIntensity, vColor.a);
        }
    """.trimIndent()
    
    // OPENGL HANDLES AND STATE
    private var program: Int = 0              // Compiled shader program
    private var positionHandle: Int = 0       // Vertex position attribute location
    private var normalHandle: Int = 0         // Vertex normal attribute location
    private var colorHandle: Int = 0          // Color uniform location
    private var mvpMatrixHandle: Int = 0      // MVP matrix uniform location
    private var modelMatrixHandle: Int = 0    // Model matrix uniform location
    private var normalMatrixHandle: Int = 0   // Normal matrix uniform location
    private var lightPosHandle: Int = 0       // Light position uniform location
    
    // 3D TRANSFORMATION MATRICES
    private val mvpMatrix = FloatArray(16)        // Final Model-View-Projection matrix
    private val projectionMatrix = FloatArray(16) // Camera projection (perspective)
    private val viewMatrix = FloatArray(16)       // Camera view transformation
    private val modelMatrix = FloatArray(16)      // Model world transformation
    private val normalMatrix = FloatArray(16)     // Normal transformation matrix
    
    // ANIMATION STATE
    @Volatile // Ensures thread-safe access from UI thread
    private var shapeKeyValue = 0f
    
    // 3D MODEL DATA
    private var headModel: GLBModel? = null
    private lateinit var glbLoader: GLBLoader
    
    // VERTEX BUFFERS
    private lateinit var vertexBuffer: FloatBuffer        // Base vertex positions
    private lateinit var morphedVertexBuffer: FloatBuffer // Morphed vertex positions (for animation)
    private lateinit var normalBuffer: FloatBuffer        // Vertex normals for lighting
    private lateinit var indexBuffer: ByteBuffer          // Triangle indices
    
    /**
     * Called when OpenGL surface is created. Initialize OpenGL state and load resources.
     */
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Set clear color and enable depth testing for 3D rendering
        GLES20.glClearColor(0.2f, 0.2f, 0.2f, 1.0f)  // Dark gray background
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)         // Enable Z-buffer for proper 3D depth
        
        // Load the 3D head model from assets
        glbLoader = GLBLoader(context)
        headModel = glbLoader.loadGLB("head.glb")
        
        // Prepare vertex buffers for rendering
        initializeBuffers()
        
        // SHADER COMPILATION AND LINKING
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        
        // Create and link shader program
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        
        // Get shader variable locations for later use
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        normalHandle = GLES20.glGetAttribLocation(program, "vNormal")
        colorHandle = GLES20.glGetUniformLocation(program, "vColor")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        modelMatrixHandle = GLES20.glGetUniformLocation(program, "uModelMatrix")
        normalMatrixHandle = GLES20.glGetUniformLocation(program, "uNormalMatrix")
        lightPosHandle = GLES20.glGetUniformLocation(program, "uLightPos")
    }
    
    /**
     * Called every frame to render the 3D scene.
     */
    override fun onDrawFrame(gl: GL10?) {
        // Clear the screen and depth buffer
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        
        // Use our compiled shader program
        GLES20.glUseProgram(program)
        
        // SHAPE KEY ANIMATION
        // Update vertex positions based on current shape key value
        updateMorphedVertices(shapeKeyValue)
        
        // 3D TRANSFORMATIONS
        // Reset model matrix (no rotation or scaling)
        Matrix.setIdentityM(modelMatrix, 0)
        
        // Set camera position - positioned to the right side looking at origin
        Matrix.setLookAtM(viewMatrix, 0, 
            3f, 0f, 0f,  // Camera position (x, y, z)
            0f, 0f, 0f,  // Look at point (origin)
            0f, 1f, 0f   // Up vector (Y-axis up)
        )
        
        // Combine projection and view matrices, then multiply by model matrix
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, mvpMatrix, 0, modelMatrix, 0)
        
        // Calculate normal matrix for correct lighting (inverse transpose of model matrix)
        Matrix.invertM(normalMatrix, 0, modelMatrix, 0)
        Matrix.transposeM(normalMatrix, 0, normalMatrix, 0)
        
        // SHADER UNIFORMS
        // Pass transformation matrices to shaders
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(modelMatrixHandle, 1, false, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(normalMatrixHandle, 1, false, normalMatrix, 0)
        
        // Set light position (top-right-front of model)
        GLES20.glUniform3fv(lightPosHandle, 1, floatArrayOf(5f, 5f, 5f), 0)
        
        // Set material color (from GLB file or default gray)
        val modelColor = headModel?.materialColor ?: floatArrayOf(0.8f, 0.8f, 0.8f, 1.0f)
        GLES20.glUniform4fv(colorHandle, 1, modelColor, 0)
        
        // VERTEX DATA
        // Enable vertex attribute arrays and bind buffers
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, morphedVertexBuffer)
        
        GLES20.glEnableVertexAttribArray(normalHandle)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 12, normalBuffer)
        
        // RENDERING
        // Draw the 3D model using indexed triangles
        headModel?.let { model ->
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, model.indices.size, GLES20.GL_UNSIGNED_INT, indexBuffer)
        }
        
        // Clean up - disable vertex attribute arrays
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
    }
    
    private fun initializeBuffers() {
        headModel?.let { model ->
            // Initialize vertex buffer
            vertexBuffer = ByteBuffer.allocateDirect(model.vertices.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            vertexBuffer.put(model.vertices)
            vertexBuffer.position(0)
            
            // Initialize morphed vertex buffer (initially same as base vertices)
            morphedVertexBuffer = ByteBuffer.allocateDirect(model.vertices.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            morphedVertexBuffer.put(model.vertices)
            morphedVertexBuffer.position(0)
            
            // Initialize normal buffer
            normalBuffer = ByteBuffer.allocateDirect(model.normals.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            normalBuffer.put(model.normals)
            normalBuffer.position(0)
            
            // Initialize index buffer
            indexBuffer = ByteBuffer.allocateDirect(model.indices.size * 4)
                .order(ByteOrder.nativeOrder())
            model.indices.forEach { index ->
                indexBuffer.putInt(index)
            }
            indexBuffer.position(0)
        }
    }
    
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.toFloat()
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 1f, 20f)
    }
    
    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        return shader
    }
    
    /**
     * Thread-safe method to update shape key value from UI thread.
     * @param value Shape key weight from 0.0 (base) to 1.0 (full morph)
     */
    fun setShapeKeyValue(value: Float) {
        shapeKeyValue = value
    }
    
    private fun updateMorphedVertices(morphWeight: Float) {
        headModel?.let { model ->
            if (model.morphTargets.isNotEmpty()) {
                // Get base vertices
                val baseVertices = model.vertices
                val morphTarget = model.morphTargets[0] // Use first morph target (KeyOne)
                
                // Interpolate between base and morph target
                val morphedVertices = FloatArray(baseVertices.size)
                for (i in baseVertices.indices) {
                    // Linear interpolation: base + (target - base) * weight
                    // Since morph targets are delta values, we add them directly
                    morphedVertices[i] = baseVertices[i] + morphTarget[i] * morphWeight
                }
                
                // Update the buffer
                morphedVertexBuffer.clear()
                morphedVertexBuffer.put(morphedVertices)
                morphedVertexBuffer.position(0)
            } else {
                // Fallback: simulate shape key by modifying vertices procedurally
                val baseVertices = model.vertices
                val morphedVertices = FloatArray(baseVertices.size)
                
                // Apply morphing to simulate facial expression change
                for (i in 0 until baseVertices.size step 3) {
                    val x = baseVertices[i]
                    val y = baseVertices[i + 1]
                    val z = baseVertices[i + 2]
                    
                    // Simulate mouth/cheek area morphing
                    // Only affect vertices in the lower face area (y < 0)
                    if (y < 0.0f && kotlin.math.abs(z) < 0.5f) {
                        // Create a smile-like deformation
                        val distanceFromCenter = kotlin.math.sqrt(x * x + z * z)
                        val influence = kotlin.math.max(0.0f, 1.0f - distanceFromCenter / 0.5f)
                        
                        morphedVertices[i] = x + x * morphWeight * 0.1f * influence // Widen slightly
                        morphedVertices[i + 1] = y + morphWeight * 0.15f * influence // Lift corners
                        morphedVertices[i + 2] = z
                    } else {
                        // Keep other vertices unchanged
                        morphedVertices[i] = x
                        morphedVertices[i + 1] = y
                        morphedVertices[i + 2] = z
                    }
                }
                
                // Using procedural morphing as fallback when no morph targets found
                
                morphedVertexBuffer.clear()
                morphedVertexBuffer.put(morphedVertices)
                morphedVertexBuffer.position(0)
            }
        }
    }
}