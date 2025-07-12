# Android GLB Shape Key Animation

A comprehensive Android application demonstrating 3D model loading and shape key (morph target) animation using OpenGL ES 2.0 and Jetpack Compose.

## Features

- **GLB Model Loading**: Parse and load 3D models in GLB (Binary GLTF) format
- **Shape Key Animation**: Real-time vertex morphing for facial expressions and animations
- **Interactive Controls**: Manual slider control and automatic animation modes
- **Modern UI**: Jetpack Compose integration with native OpenGL ES rendering
- **Comprehensive Comments**: Extensively documented code for learning

## Demo

The app loads a 3D head model and allows you to:
- Manually control shape key values with a slider (0.0 to 1.0)
- Enable auto-animation for smooth looping transitions
- See real-time 3D morphing with proper lighting

## Technical Architecture

### Key Components

1. **MainActivity.kt**
   - Jetpack Compose UI with state management
   - Coroutine-based animation loops
   - OpenGL ES integration via AndroidView

2. **GLBLoader.kt** 
   - Binary GLB format parser
   - JSON metadata extraction
   - Morph target (shape key) support
   - Fallback model generation

3. **HeadRenderer.kt** (within MainActivity)
   - OpenGL ES 2.0 renderer
   - Vertex shader with lighting calculations
   - Real-time vertex morphing
   - MVP matrix transformations

### Learning Concepts Demonstrated

- **3D Graphics**: OpenGL ES shaders, vertex buffers, matrix math
- **File Parsing**: Binary format parsing, JSON processing
- **Android UI**: Jetpack Compose state management and lifecycle
- **Animation**: Coroutine-based smooth animation loops
- **Performance**: Efficient 3D rendering and memory management

## Project Structure

```
app/src/main/
├── assets/
│   └── head.glb                 # 3D model with shape keys
├── java/com/rr/dhead/
│   ├── MainActivity.kt          # Main UI and OpenGL integration
│   └── GLBLoader.kt            # GLB file format parser
└── res/                        # Android resources
```

## Setup Instructions

1. **Prerequisites**
   - Android Studio 2024.1+
   - Minimum SDK: API 24 (Android 7.0)
   - Target SDK: API 36

2. **Clone and Build**
   ```bash
   git clone https://github.com/robertrahardja/androidgltb.git
   cd androidgltb
   ./gradlew assembleDebug
   ```

3. **Install on Device**
   ```bash
   ./gradlew installDebug
   # or use the deploy script
   ./deploy.sh
   ```

## Code Highlights

### Shape Key Animation
```kotlin
// Smooth sine wave animation loop
LaunchedEffect(autoAnimate) {
    if (autoAnimate) {
        val startTime = System.currentTimeMillis()
        while (isActive) {
            val elapsed = (System.currentTimeMillis() - startTime) * 0.002f
            val newValue = (sin(elapsed) + 1f) / 2f // Map sin(-1,1) to (0,1)
            shapeKeyValue = newValue
            renderer.setShapeKeyValue(newValue)
            delay(16) // ~60fps
        }
    }
}
```

### Vertex Morphing
```kotlin
// Linear interpolation between base and morph target vertices
for (i in baseVertices.indices) {
    morphedVertices[i] = baseVertices[i] + morphTarget[i] * morphWeight
}
```

### OpenGL Shader Integration
```glsl
// Vertex shader with lighting calculations
attribute vec4 vPosition;
attribute vec3 vNormal;
uniform mat4 uMVPMatrix;
uniform vec3 uLightPos;
varying float vLightIntensity;

void main() {
    gl_Position = uMVPMatrix * vPosition;
    // Calculate Lambertian lighting
    vec3 lightDir = normalize(uLightPos - worldPos.xyz);
    vLightIntensity = max(dot(worldNormal, lightDir), 0.2);
}
```

## Educational Value

This project serves as a comprehensive example for:

- **Graphics Programming Students**: Real-world OpenGL ES implementation
- **Android Developers**: Modern Compose + native integration patterns  
- **3D Graphics**: Shape key animation and vertex morphing techniques
- **File Format Parsing**: Binary format handling and error recovery

## Requirements

- Android device with OpenGL ES 2.0 support
- Minimum 1GB RAM recommended
- ARMv7 or ARM64 processor

## License

MIT License - Feel free to use this project for learning and development.

## Contributing

Pull requests welcome! Areas for improvement:
- Additional shape key blending modes
- Multiple model loading
- Texture support
- More animation easing functions

## Technical Notes

The GLB parser supports:
- Standard vertex attributes (position, normal)
- Multiple index types (byte, short, int)
- Sparse and dense morph targets
- Material color extraction
- Graceful fallback on parsing errors

The OpenGL renderer implements:
- Efficient vertex buffer management
- Real-time vertex morphing
- Basic Lambertian lighting
- Thread-safe state updates from UI thread
