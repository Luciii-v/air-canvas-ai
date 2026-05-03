# Air Canvas AI Pro 🎨 · Cinematic Edition

A futuristic, high-performance virtual air-drawing application. Create stunning light paintings in real-time using advanced computer vision and cinematic rendering — no mouse, no stylus, just your hands and a webcam.

---

## V2 Architecture Overhaul

```
Webcam → Python (MediaPipe V2) ──UDP:5005──► JavaFX / Swing Hybrid
            AI Brain (Threaded)              Cinematic Drawing Surface
         backend/handtracker.py          src/.../App.java + DashboardUI.java
```

| Layer | Tech | Role |
|---|---|---|
| **AI Brain** | Python · MediaPipe · OpenCV | Threaded frame capture, 21-point landmark detection, pinch-to-size logic, gesture hysteresis. |
| **Network** | UDP Socket (Port 5005) | High-density packet pipeline transmitting full hand skeletal state and telemetry. |
| **Renderer** | Java · Graphics2D · Swing | Velocity-based light painting, Quadratic Bezier smoothing, temporal decay (10s), alpha-layered neon tubes. |
| **Telemetry** | JavaFX · CSS · FXML | Glassmorphism dashboard, real-time FPS/Mode tracking, dynamic thickness progress bars. |

---

## Advanced Gestures

| Gesture | Action |
|---|---|
| ☝️ **Index only** | **DRAW** — paint with cinematic sunset neon strokes. |
| 🤏 **Pinch/Spread** | **DYNAMIC SIZE** — pinch thumb & index to shrink, spread to thicken (0.2x to 2.5x). |
| ✌️ **Peace Sign** | **ERASE** — remove light strokes with a precision eraser ring. |
| ✋ **Open Palm (Still)** | **EXPORT** — hold still for 2s to "snap" a high-res PNG with a fiery orange flash effect. |
| 🖐️ **Other** | **HOVER** — move the glowing Hellfire skeleton without drawing. |

---

## Modern Features

- **Velocity Physics:** Hand speed dictates stroke behavior. Fast movements create kinetic particle scatter; slow movements create intense "burn-in" glow.
- **Bezier Smoothing:** Linear segments are replaced by Quadratic Bezier curves for perfectly fluid, jitter-free geometry.
- **Hellfire Skeleton:** 21-point skeletal tracking rendered as a superheated white-hot core with a pulsing crimson aura and joint-based fire particle emitters.
- **Temporal Decay:** Living canvas where older strokes slowly fade into darkness over 10 seconds, creating a dynamic light-sculpture effect.
- **Glassmorphism UI:** Floating telemetry dashboard featuring frosted glass panels, modern typography, and smooth fade transitions.

---

## Setup

### Prerequisites
- Python 3.9+
- Java 8+
- Maven

### 1 · Prepare isolated backend
```bash
cd air-canvas-ai/backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

### 2 · Build the cinematic canvas
```bash
mvn compile
```

---

## Running

The project is optimized for **VS Code**. Simply select the **"Run Full AI Canvas"** compound configuration from the *Run and Debug* view to launch both processes with one click.

Manually:

**Terminal 1 — AI Brain (Python)**
```bash
./air-canvas-ai/backend/.venv/bin/python air-canvas-ai/backend/handtracker.py
```

**Terminal 2 — Drawing Canvas (Java)**
```bash
mvn exec:java -Dexec.mainClass="com.aicanvas.App"
```

---

## Keyboard Shortcuts

| Key | Action |
|---|---|
| `C` | Clear the entire canvas |
| `S` | Manual Save (Legacy Mode) |
| `ESC` | Quit tracker (in webcam window) |

---

## How It Works

1. **Python Engine:** Utilizes a threaded `VideoStream` and `model_complexity=0` for minimum latency. Transmits a high-density `gesture,thickness|x1,y1...x21,y21` packet string.
2. **Java Receiver:** A dedicated UDP listener thread parses the 44-point skeletal manifold and pipes it into the JavaFX/Swing hybrid loop at 30+ FPS.
3. **Cinematic Pipeline:** Applies velocity-based thickness scaling, Bezier path interpolation, and a multi-pass glow shader before rendering to a temporal stroke list with lifecycle management.
