📏 Measure Me

An AR-powered Android application that allows users to measure real-world objects instantly using their smartphone camera.

Built using Native Android (Kotlin) + ARCore, this app brings precise spatial measurement into your pocket.

🚀 Features
📐 Real-time distance measurement between two points
🧊 Automatic plane detection (floor, wall, table)
📏 AR-based measurement line rendering
📷 Capture and save measurement screenshots
📱 Simple and minimal UI/UX design
🔄 Reset and re-measure anytime
🧭 Built-in level tool (bubble alignment)
🧠 How It Works
Open the camera in AR mode
Point at a surface to detect planes
Tap to set start point
Move device and tap end point
App calculates real-world distance using ARCore tracking
⚙️ Tech Stack
Kotlin (Native Android)
ARCore (Google AR SDK)
CameraX (optional camera handling)
Sceneform / OpenGL (for rendering)
SensorManager (for level tool)
📦 Architecture
Camera Input
   ↓
ARCore Tracking (SLAM)
   ↓
Plane Detection
   ↓
HitTest (2D → 3D conversion)
   ↓
Measurement Engine
   ↓
UI Renderer (Lines + Labels)
📏 Measurement Formula

Distance is calculated using 3D Euclidean distance:

distance = √((x2-x1)² + (y2-y1)² + (z2-z1)²)

Converted into:

Meters
Centimeters
Inches
🧪 Accuracy Improvements
Feature point filtering
Multi-sample averaging
Depth API support (on supported devices)
Tracking quality validation
📱 Screens / UI
Home screen with start measurement button
AR camera measurement view
Live distance overlay
Level tool screen
Screenshot capture view
⚠️ Limitations
Accuracy depends on device camera quality
Works best in well-lit environments
ARCore supported devices required
Measurements are approximate, not certified
🔮 Future Improvements
AI-based object size estimation
Room scanning mode
3D floor plan export
Cloud save of measurements
Multi-point measurement paths
👨‍💻 Author

Built by Ankush Pratap
