package com.rr.dhead

import android.content.Context
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.experimental.and

/**
 * Data class representing a loaded 3D model with optional shape keys (morph targets).
 * 
 * @param vertices Base vertex positions (x,y,z coordinates)
 * @param normals Vertex normals for lighting calculations
 * @param indices Triangle indices (3 per triangle)
 * @param vertexCount Number of vertices in the model
 * @param faceCount Number of triangular faces
 * @param materialColor RGBA color from the model material
 * @param morphTargets List of morph target vertex deltas for animation
 * @param morphTargetNames Human-readable names for each shape key
 */
data class GLBModel(
    val vertices: FloatArray,
    val normals: FloatArray,
    val indices: IntArray,
    val vertexCount: Int,
    val faceCount: Int,
    val materialColor: FloatArray = floatArrayOf(0.8f, 0.8f, 0.8f, 1.0f),
    val morphTargets: List<FloatArray> = emptyList(),
    val morphTargetNames: List<String> = emptyList()
)

/**
 * GLB (GLTF Binary) model loader for Android.
 * 
 * This class demonstrates:
 * - Binary file format parsing
 * - JSON parsing for 3D scene data
 * - Vertex buffer management
 * - Shape key (morph target) extraction
 * - Error handling with fallback models
 */
class GLBLoader(private val context: Context) {
    
    /**
     * Loads a GLB file from the app's assets folder.
     * 
     * @param filename Name of the GLB file in assets (e.g., "head.glb")
     * @return GLBModel containing mesh data, or fallback model if loading fails
     */
    fun loadGLB(filename: String): GLBModel? {
        return try {
            // Load binary data from assets
            val inputStream = context.assets.open(filename)
            val bytes = inputStream.readBytes()
            inputStream.close()
            
            // Parse the GLB binary format
            parseGLB(bytes) ?: createFallbackModel()
            
        } catch (e: Exception) {
            // If anything goes wrong, use a simple fallback model
            e.printStackTrace()
            createFallbackModel()
        }
    }
    
    /**
     * Parses GLB binary format.
     * GLB structure: [Header][JSON Chunk][Binary Chunk]
     */
    private fun parseGLB(data: ByteArray): GLBModel? {
        return try {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            
            // GLB HEADER (12 bytes)
            val magic = buffer.int    // Magic number: "glTF" (0x46546C67)
            val version = buffer.int  // GLB version (should be 2)
            val length = buffer.int   // Total file length
            
            // Validate GLB magic number
            if (magic != 0x46546C67) {
                return createFallbackModel()
            }
            
            // JSON CHUNK (contains scene description)
            val jsonChunkLength = buffer.int
            val jsonChunkType = buffer.int // "JSON" chunk type
            
            // Extract and parse JSON data
            val jsonBytes = ByteArray(jsonChunkLength)
            buffer.get(jsonBytes)
            val jsonString = String(jsonBytes, Charsets.UTF_8)
            val gltf = JSONObject(jsonString)
            
            // BINARY CHUNK (contains vertex/index data)
            val binaryChunkLength = buffer.int
            val binaryChunkType = buffer.int // "BIN\0" chunk type
            
            // Extract binary data
            val binaryData = ByteArray(binaryChunkLength)
            buffer.get(binaryData)
            val binaryBuffer = ByteBuffer.wrap(binaryData).order(ByteOrder.LITTLE_ENDIAN)
            
            // Parse mesh data from JSON + binary
            return parseActualMesh(gltf, binaryBuffer)
            
        } catch (e: Exception) {
            e.printStackTrace()
            createFallbackModel()
        }
    }
    
    /**
     * Parses mesh data from GLTF JSON and binary buffer.
     * Extracts vertices, normals, indices, materials, and shape keys.
     */
    private fun parseActualMesh(gltf: JSONObject, binaryBuffer: ByteBuffer): GLBModel? {
        try {
            // Get the first mesh from the GLTF scene
            val meshes = gltf.getJSONArray("meshes")
            if (meshes.length() == 0) {
                return createFallbackModel()
            }
            
            val mesh = meshes.getJSONObject(0)
            val primitives = mesh.getJSONArray("primitives")
            val primitive = primitives.getJSONObject(0)
            
            // Get material information
            val materialIndex = primitive.optInt("material", -1)
            val materialColor = if (materialIndex >= 0) {
                extractMaterialColor(gltf, materialIndex)
            } else {
                floatArrayOf(0.8f, 0.8f, 0.8f, 1.0f) // Default gray
            }
            
            val attributes = primitive.getJSONObject("attributes")
            val positionAccessor = attributes.getInt("POSITION")
            val normalAccessor = if (attributes.has("NORMAL")) attributes.getInt("NORMAL") else -1
            val indicesAccessor = primitive.getInt("indices")
            
            val accessors = gltf.getJSONArray("accessors")
            val bufferViews = gltf.getJSONArray("bufferViews")
            
            // Get position data
            val posAccessor = accessors.getJSONObject(positionAccessor)
            val posBufferView = bufferViews.getJSONObject(posAccessor.getInt("bufferView"))
            val posOffset = posBufferView.optInt("byteOffset", 0)
            val vertexCount = posAccessor.getInt("count")
            
            val vertices = FloatArray(vertexCount * 3)
            binaryBuffer.position(posOffset)
            for (i in 0 until vertexCount * 3) {
                vertices[i] = binaryBuffer.float
            }
            // Successfully loaded vertex data
            
            // Get normal data (or generate if not present)
            val normals = if (normalAccessor >= 0) {
                val normAccessor = accessors.getJSONObject(normalAccessor)
                val normBufferView = bufferViews.getJSONObject(normAccessor.getInt("bufferView"))
                val normOffset = normBufferView.optInt("byteOffset", 0)
                
                val normalsArray = FloatArray(vertexCount * 3)
                binaryBuffer.position(normOffset)
                for (i in 0 until vertexCount * 3) {
                    normalsArray[i] = binaryBuffer.float
                }
                normalsArray
            } else {
                // Generate simple normals if not present
                generateNormals(vertices, vertexCount)
            }
            
            // Get indices data
            val indAccessor = accessors.getJSONObject(indicesAccessor)
            val indBufferView = bufferViews.getJSONObject(indAccessor.getInt("bufferView"))
            val indOffset = indBufferView.optInt("byteOffset", 0)
            val indexCount = indAccessor.getInt("count")
            
            val indices = IntArray(indexCount)
            binaryBuffer.position(indOffset)
            
            // Handle different index data types
            val componentType = indAccessor.getInt("componentType")
            when (componentType) {
                5121 -> { // UNSIGNED_BYTE
                    for (i in 0 until indexCount) {
                        indices[i] = binaryBuffer.get().toInt() and 0xFF
                    }
                }
                5123 -> { // UNSIGNED_SHORT
                    for (i in 0 until indexCount) {
                        indices[i] = binaryBuffer.short.toInt() and 0xFFFF
                    }
                }
                5125 -> { // UNSIGNED_INT
                    for (i in 0 until indexCount) {
                        indices[i] = binaryBuffer.int
                    }
                }
                else -> {
                    return createFallbackModel()
                }
            }
            
            // Extract morph targets (shape keys)
            val morphTargets = mutableListOf<FloatArray>()
            val morphTargetNames = mutableListOf<String>()
            
            if (primitive.has("targets")) {
                try {
                    val targets = primitive.getJSONArray("targets")
                    // Process morph targets (shape keys)
                    for (i in 0 until targets.length()) {
                        val target = targets.getJSONObject(i)
                        if (target.has("POSITION")) {
                            val morphPositionAccessor = target.getInt("POSITION")
                            val morphPosAccessor = accessors.getJSONObject(morphPositionAccessor)
                            
                            // Check if this accessor has a bufferView (some morph targets might be sparse)
                            if (morphPosAccessor.has("bufferView")) {
                                val morphPosBufferView = bufferViews.getJSONObject(morphPosAccessor.getInt("bufferView"))
                                val morphPosOffset = morphPosBufferView.optInt("byteOffset", 0)
                                val morphVertexCount = morphPosAccessor.getInt("count")
                                
                                // Load morph target vertex data
                                
                                // Ensure we don't read beyond buffer bounds
                                if (morphVertexCount == vertexCount) {
                                    val morphVertices = FloatArray(morphVertexCount * 3)
                                    binaryBuffer.position(morphPosOffset)
                                    for (j in 0 until morphVertexCount * 3) {
                                        morphVertices[j] = binaryBuffer.float
                                    }
                                    morphTargets.add(morphVertices)
                                    
                                    // Try to get shape key name from extras
                                    val morphName = if (mesh.has("extras") && mesh.getJSONObject("extras").has("targetNames")) {
                                        val targetNames = mesh.getJSONObject("extras").getJSONArray("targetNames")
                                        if (i < targetNames.length()) targetNames.getString(i) else "MorphTarget$i"
                                    } else {
                                        "MorphTarget$i"
                                    }
                                    morphTargetNames.add(morphName)
                                    // Successfully loaded morph target
                                } else {
                                    // Vertex count mismatch - skip this morph target
                                }
                            } else if (morphPosAccessor.has("sparse")) {
                                // Handle sparse accessor (advanced GLTF feature)
                                val sparse = morphPosAccessor.getJSONObject("sparse")
                                val count = sparse.getInt("count")
                                val morphVertexCount = morphPosAccessor.getInt("count")
                                
                                if (morphVertexCount == vertexCount && count > 0) {
                                    // Parse sparse indices
                                    val indices = sparse.getJSONObject("indices")
                                    val indicesBufferView = indices.getInt("bufferView")
                                    val indicesComponentType = indices.getInt("componentType")
                                    val indicesBufferViewObj = bufferViews.getJSONObject(indicesBufferView)
                                    val indicesOffset = indicesBufferViewObj.optInt("byteOffset", 0)
                                    
                                    // Parse sparse values
                                    val values = sparse.getJSONObject("values")
                                    val valuesBufferView = values.getInt("bufferView")
                                    val valuesBufferViewObj = bufferViews.getJSONObject(valuesBufferView)
                                    val valuesOffset = valuesBufferViewObj.optInt("byteOffset", 0)
                                    
                                    // Create morph target array (start with zeros - sparse means only some vertices are modified)
                                    val morphVertices = FloatArray(morphVertexCount * 3)
                                    
                                    // Read sparse indices
                                    binaryBuffer.position(indicesOffset)
                                    val sparseIndices = IntArray(count)
                                    when (indicesComponentType) {
                                        5121 -> { // UNSIGNED_BYTE
                                            for (j in 0 until count) {
                                                sparseIndices[j] = binaryBuffer.get().toInt() and 0xFF
                                            }
                                        }
                                        5123 -> { // UNSIGNED_SHORT
                                            for (j in 0 until count) {
                                                sparseIndices[j] = binaryBuffer.short.toInt() and 0xFFFF
                                            }
                                        }
                                        5125 -> { // UNSIGNED_INT
                                            for (j in 0 until count) {
                                                sparseIndices[j] = binaryBuffer.int
                                            }
                                        }
                                    }
                                    
                                    // Read sparse values
                                    binaryBuffer.position(valuesOffset)
                                    for (j in 0 until count) {
                                        val vertexIndex = sparseIndices[j]
                                        if (vertexIndex < morphVertexCount) {
                                            morphVertices[vertexIndex * 3] = binaryBuffer.float     // X
                                            morphVertices[vertexIndex * 3 + 1] = binaryBuffer.float // Y
                                            morphVertices[vertexIndex * 3 + 2] = binaryBuffer.float // Z
                                        }
                                    }
                                    
                                    morphTargets.add(morphVertices)
                                    
                                    // Try to get shape key name from extras
                                    val morphName = if (mesh.has("extras") && mesh.getJSONObject("extras").has("targetNames")) {
                                        val targetNames = mesh.getJSONObject("extras").getJSONArray("targetNames")
                                        if (i < targetNames.length()) targetNames.getString(i) else "MorphTarget$i"
                                    } else {
                                        "MorphTarget$i"
                                    }
                                    morphTargetNames.add(morphName)
                                    // Successfully loaded sparse morph target
                                } else {
                                    // Sparse morph target has issues - skip
                                }
                            } else {
                                // Morph target has no data - skip
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Continue without morph targets if parsing fails
                }
            }
            
            return GLBModel(
                vertices = vertices,
                normals = normals,
                indices = indices,
                vertexCount = vertexCount,
                faceCount = indexCount / 3,
                materialColor = materialColor,
                morphTargets = morphTargets,
                morphTargetNames = morphTargetNames
            )
            
        } catch (e: Exception) {
            e.printStackTrace()
            return createFallbackModel()
        }
    }
    
    private fun createHeadModel(): GLBModel {
        // Create a head-like shape using an elongated sphere
        val vertices = mutableListOf<Float>()
        val indices = mutableListOf<Int>()
        
        val latitudeBands = 16
        val longitudeBands = 16
        val radius = 1.2f
        
        // Generate vertices for a head-like ellipsoid
        for (lat in 0..latitudeBands) {
            val theta = lat * Math.PI / latitudeBands
            val sinTheta = kotlin.math.sin(theta).toFloat()
            val cosTheta = kotlin.math.cos(theta).toFloat()
            
            for (long in 0..longitudeBands) {
                val phi = long * 2 * Math.PI / longitudeBands
                val sinPhi = kotlin.math.sin(phi).toFloat()
                val cosPhi = kotlin.math.cos(phi).toFloat()
                
                // Make it more head-like by stretching Y and making it wider at top
                val x = cosPhi * sinTheta * radius * 0.8f // Narrower sides
                val y = cosTheta * radius * 1.3f // Taller
                val z = sinPhi * sinTheta * radius * 0.9f // Slightly compressed front-back
                
                vertices.addAll(listOf(x, y, z))
            }
        }
        
        // Generate indices for triangular faces
        for (lat in 0 until latitudeBands) {
            for (long in 0 until longitudeBands) {
                val first = lat * (longitudeBands + 1) + long
                val second = first + longitudeBands + 1
                
                // First triangle
                indices.addAll(listOf(first, second, first + 1))
                // Second triangle  
                indices.addAll(listOf(second, second + 1, first + 1))
            }
        }
        
        val verticesArray = vertices.toFloatArray()
        val generatedNormals = generateNormals(verticesArray, vertices.size / 3)
        
        return GLBModel(
            vertices = verticesArray,
            normals = generatedNormals,
            indices = indices.toIntArray(),
            vertexCount = vertices.size / 3,
            faceCount = indices.size / 3
        )
    }
    
    private fun createFallbackModel(): GLBModel {
        // Simple cube fallback
        val vertices = floatArrayOf(
            -1.0f, -1.0f, -1.0f,   // 0
             1.0f, -1.0f, -1.0f,   // 1
             1.0f,  1.0f, -1.0f,   // 2
            -1.0f,  1.0f, -1.0f,   // 3
            -1.0f, -1.0f,  1.0f,   // 4
             1.0f, -1.0f,  1.0f,   // 5
             1.0f,  1.0f,  1.0f,   // 6
            -1.0f,  1.0f,  1.0f    // 7
        )
        
        val indices = intArrayOf(
            0, 1, 2,  0, 2, 3,  // Front
            4, 6, 5,  4, 7, 6,  // Back  
            4, 0, 3,  4, 3, 7,  // Left
            1, 5, 6,  1, 6, 2,  // Right
            3, 2, 6,  3, 6, 7,  // Top
            4, 5, 1,  4, 1, 0   // Bottom
        )
        
        val generatedNormals = generateNormals(vertices, 8)
        
        return GLBModel(
            vertices = vertices,
            normals = generatedNormals,
            indices = indices,
            vertexCount = 8,
            faceCount = 12
        )
    }
    
    private fun generateNormals(vertices: FloatArray, vertexCount: Int): FloatArray {
        // Simple normal generation - just point outward from center
        val normals = FloatArray(vertexCount * 3)
        
        for (i in 0 until vertexCount) {
            val x = vertices[i * 3]
            val y = vertices[i * 3 + 1]
            val z = vertices[i * 3 + 2]
            
            // Calculate distance from origin
            val length = kotlin.math.sqrt(x * x + y * y + z * z)
            
            if (length > 0) {
                normals[i * 3] = x / length
                normals[i * 3 + 1] = y / length
                normals[i * 3 + 2] = z / length
            } else {
                normals[i * 3] = 0f
                normals[i * 3 + 1] = 1f
                normals[i * 3 + 2] = 0f
            }
        }
        
        return normals
    }
    
    private fun extractMaterialColor(gltf: JSONObject, materialIndex: Int): FloatArray {
        return try {
            val materials = gltf.getJSONArray("materials")
            val material = materials.getJSONObject(materialIndex)
            
            // Try to get base color from PBR metallic roughness
            if (material.has("pbrMetallicRoughness")) {
                val pbr = material.getJSONObject("pbrMetallicRoughness")
                if (pbr.has("baseColorFactor")) {
                    val colorArray = pbr.getJSONArray("baseColorFactor")
                    floatArrayOf(
                        colorArray.getDouble(0).toFloat(),
                        colorArray.getDouble(1).toFloat(),
                        colorArray.getDouble(2).toFloat(),
                        if (colorArray.length() > 3) colorArray.getDouble(3).toFloat() else 1.0f
                    )
                } else {
                    floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f) // Default white
                }
            }
            // Try legacy diffuse factor
            else if (material.has("diffuseFactor")) {
                val colorArray = material.getJSONArray("diffuseFactor")
                floatArrayOf(
                    colorArray.getDouble(0).toFloat(),
                    colorArray.getDouble(1).toFloat(),
                    colorArray.getDouble(2).toFloat(),
                    if (colorArray.length() > 3) colorArray.getDouble(3).toFloat() else 1.0f
                )
            }
            // Default white if no color found
            else {
                floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            floatArrayOf(0.8f, 0.8f, 0.8f, 1.0f) // Default gray on error
        }
    }
}