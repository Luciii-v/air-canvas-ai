# Air Canvas AI 🎨

A virtual air-drawing application powered by computer vision. Draw on a digital canvas in real-time using only your hand gestures — no mouse, no stylus, just your fingers and a webcam.

---

## Architecture

```
Webcam → Python (MediaPipe) ──UDP:5005──► Java Swing Canvas
            AI Brain                         Drawing Surface
         backend/handtracker.py          src/.../App.java
```

Two independent processes communicate over a local UDP socket on port **5005**.

| Layer | Tech | Role |
|---|---|---|
| Computer Vision | Python · OpenCV · MediaPipe | Hand landmark detection, gesture classification, coordinate smoothing |
| Canvas UI | Java · Swing · Graphics2D | Real-time drawing surface, neon stroke rendering, HUD |

---

## Gestures

| Gesture | Fingers Up | Action |
|---|---|---|
| ☝️ Index only | 1 | **DRAW** — paint rainbow neon strokes |
| ✌️ Index + Middle | 2 | **HOVER** — move cursor without drawing |
| 🤟 Index + Middle + Ring | 3 | **ERASE** — erase underneath the cursor |

---

## Setup

### Prerequisites
- Python 3.9+
- Java 8+
- Maven

### 1 · Install Python dependencies
```bash
cd backend
pip install -r requirements.txt
```

### 2 · Build the Java canvas
```bash
mvn compile
```

---

## Running

Open **two terminals** from the repo root:

**Terminal 1 — AI Brain (Python)**
```bash
python3 backend/handtracker.py
```

**Terminal 2 — Drawing Canvas (Java)**
```bash
mvn exec:java -Dexec.mainClass="com.aicanvas.App"
```

> macOS: The first time you run the Python script, grant **camera access** to Terminal in  
> System Settings → Privacy & Security → Camera.

---

## Keyboard Shortcuts (Canvas window)

| Key | Action |
|---|---|
| `C` | Clear the canvas |
| `S` | Save canvas as PNG to Desktop |
| `ESC` (in webcam window) | Quit the Python tracker |

---

## Project Structure

```
air-canvas-ai/
├── backend/
│   ├── handtracker.py      # Python AI brain — MediaPipe + UDP sender
│   └── requirements.txt    # Python dependencies
├── src/main/java/com/aicanvas/
│   ├── App.java            # Entry point — JFrame, timer loop, keyboard shortcuts
│   ├── DrawingCanvas.java  # Swing panel — neon stroke rendering, HUD, eraser
│   └── CoordinateReceiver.java  # UDP listener on port 5005
├── pom.xml                 # Maven build config (Java 8)
└── README.md
```

---

## How It Works

1. **Python** reads webcam frames via OpenCV, runs MediaPipe hand landmark detection, applies EMA smoothing to the index finger tip coordinates, and broadcasts `x,y,GESTURE` packets over UDP.
2. **Java** listens on UDP port 5005, applies a second smoothing pass, checks a 3px deadzone, and renders neon glow segments onto a persistent `BufferedImage` using `AlphaComposite` layering.
3. The two layers of smoothing (Python EMA α=0.25 + Java MIN_MOVE=3px) eliminate hand tremor and produce smooth, consistent strokes.
