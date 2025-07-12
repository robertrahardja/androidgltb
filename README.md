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

## Dependencies and Libraries

This project uses modern Android development tools and libraries:

### Core Android Libraries
- **Android Gradle Plugin**: `8.11.1` - Build system
- **Kotlin**: `2.0.21` - Primary programming language
- **Compile SDK**: `36` - Latest Android API features
- **Min SDK**: `24` (Android 7.0) - Broad device compatibility

### Jetpack Compose UI Framework
- **Compose BOM**: `2024.09.00` - Bill of Materials for version alignment
- **Activity Compose**: `1.10.1` - Activity integration
- **UI**: Latest - Core UI components
- **UI Graphics**: Latest - Graphics and drawing APIs
- **Material3**: Latest - Material Design 3 components
- **UI Tooling**: Latest - Development tools and previews

### AndroidX Libraries
- **Core KTX**: `1.16.0` - Kotlin extensions
- **Lifecycle Runtime KTX**: `2.9.1` - Lifecycle-aware components

### Testing Dependencies
- **JUnit**: `4.13.2` - Unit testing framework
- **AndroidX JUnit**: `1.2.1` - Android testing extensions
- **Espresso Core**: `3.6.1` - UI testing framework

### Native Libraries (Built-in)
- **OpenGL ES 2.0** - 3D graphics rendering (Android system)
- **GLSurfaceView** - OpenGL surface for rendering
- **JSON** - Built-in JSON parsing for GLTF metadata

## Setup Instructions

1. **Prerequisites**
   - Android Studio 2024.1+
   - Minimum SDK: API 24 (Android 7.0)
   - Target SDK: API 36
   - Java 11 or higher

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

## Step-by-Step Gradle Integration

If you want to integrate these technologies into your own project, follow these steps:

### 1. Project-Level Configuration

**`build.gradle.kts` (Project level):**
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

**`gradle/libs.versions.toml`:**
```toml
[versions]
agp = "8.11.1"
kotlin = "2.0.21"
coreKtx = "1.16.0"
lifecycleRuntimeKtx = "2.9.1"
activityCompose = "1.10.1"
composeBom = "2024.09.00"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-material3 = { group = "androidx.compose.material3", name = "material3" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

### 2. App-Level Configuration

**`app/build.gradle.kts`:**
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "your.package.name"
    compileSdk = 36

    defaultConfig {
        applicationId = "your.package.name"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    kotlinOptions {
        jvmTarget = "11"
    }
    
    buildFeatures {
        compose = true  // Enable Jetpack Compose
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    
    // Jetpack Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    
    // For development tools
    debugImplementation(libs.androidx.ui.tooling)
}
```

### 3. OpenGL ES Integration

No additional dependencies needed for OpenGL ES - it's part of Android:

```kotlin
// In your Activity
import android.opengl.GLSurfaceView
import android.opengl.GLES20

// AndroidView for Compose integration
AndroidView(
    factory = { context ->
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)  // OpenGL ES 2.0
            setRenderer(yourRenderer)
        }
    }
)
```

### 4. Coroutines for Animation

Coroutines are included with Kotlin - no extra dependencies:

```kotlin
// In your Composable
LaunchedEffect(animationTrigger) {
    while (isActive) {
        // Animation loop
        delay(16) // 60fps
    }
}
```

### 5. File Parsing Dependencies

For GLB/JSON parsing, use built-in Android libraries:

```kotlin
// Built-in JSON parsing
import org.json.JSONObject

// Built-in file I/O
import java.nio.ByteBuffer
import java.nio.ByteOrder
```

### 6. Gradle Wrapper Configuration

**`gradle.properties`:**
```properties
android.useAndroidX=true
android.enableJetifier=true
kotlin.code.style=official
android.defaults.buildfeatures.buildconfig=true
android.nonTransitiveRClass=false
org.gradle.parallel=true
org.gradle.caching=true
```

This setup provides you with:
- ✅ Modern Kotlin development with latest features
- ✅ Jetpack Compose for declarative UI
- ✅ OpenGL ES 2.0 for high-performance 3D graphics
- ✅ Coroutines for smooth animations
- ✅ Built-in JSON parsing for 3D file formats
- ✅ Material Design 3 components

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
