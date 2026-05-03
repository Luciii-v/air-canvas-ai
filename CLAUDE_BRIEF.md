# Air Canvas AI — Full Project Brief for Claude

Paste everything below this line into Claude and ask it to make your report and PPT.

---

## CONTEXT FOR CLAUDE

I need you to make:
1. A **Project Report** (formal, comprehensive, covering implementation details and logic)
2. A **PowerPoint presentation** (PPT content covering project scope, architecture, and key features)

For a **Java course Full Project Evaluation** happening Monday April 20.

---

## PROJECT OVERVIEW

**Project Name:** Air Canvas AI  
**Type:** Computer Vision + Java Swing application  
**Purpose:** A virtual air-drawing canvas. The user waves their hand in front of a webcam and draws on a digital canvas in real-time using hand gestures — no mouse, no stylus, just fingers.

---

## ARCHITECTURE

Two independent processes communicate over a local UDP socket:

```
Webcam → Python (MediaPipe AI) ──UDP Port 5005──► Java Swing Canvas
         [backend/handtracker.py]                  [App.java + DrawingCanvas.java]
```

- **Python side** = the AI brain. Detects hand landmarks, classifies gestures, smooths coordinates, sends them to Java.
- **Java side** = the drawing surface. Receives coordinates over UDP, renders neon strokes on a BufferedImage, displays HUD.

---

## TECH STACK

| Layer | Technology |
|---|---|
| Hand Tracking | Python · OpenCV · MediaPipe Hands |
| Drawing Canvas | Java · Swing · Graphics2D · BufferedImage |
| Communication | UDP Socket (localhost:5005) |
| Build Tool | Maven (Java 8) |

---

## GESTURE SYSTEM

| Gesture | Fingers Raised | Command Sent | Effect |
|---|---|---|---|
| ☝️ Index only | 1 | DRAW | Paints neon rainbow strokes on canvas |
| ✌️ Index + Middle | 2 | HOVER | Moves cursor without drawing |
| 🤟 Index + Middle + Ring | 3 | ERASE | Erases canvas under cursor |

Gesture detection uses MediaPipe landmark y-coordinates: a finger is "up" if its TIP landmark (e.g. landmark[8]) has a lower y-value than its PIP joint (e.g. landmark[6]).

---

## KEY FEATURES

1. **Real-time hand tracking** at ~30 FPS using MediaPipe Hands
2. **Dual-layer coordinate smoothing:**
   - Python: Exponential Moving Average (EMA, α=0.25) on raw landmark coordinates
   - Java: 3px minimum movement deadzone filter
3. **Deadzone gate:** UDP packets only sent when finger moves >6px OR gesture changes — prevents micro-tremor marks
4. **Neon rainbow strokes:** Color cycles through HSB hue space (HUE_STEP=0.004 per segment), rendered with 2-layer AlphaComposite (outer glow 15% alpha + vivid core 100%)
5. **Eraser tool:** Paints with background color using a 40px rounded stroke
6. **Socket timeout reset:** Java CoordinateReceiver uses setSoTimeout(200ms) — if no UDP packet arrives in 200ms (hand left frame), coordinates reset to 0,0 and gesture resets to HOVER, preventing "snap lines" when hand re-enters
7. **Memory optimisations:**
   - BufferedImage uses TYPE_INT_RGB (24-bit) not ARGB (32-bit) — saves 25% pixel buffer RAM
   - All BasicStroke objects pre-allocated as static final constants — zero per-frame GC pressure
   - AlphaComposite instances pre-allocated as static final — no object creation inside draw loop
8. **HUD overlay:** Displays Hand status, FPS counter, current gesture — rendered as plain text on the Swing overlay (not baked into canvas image)
9. **Keyboard shortcuts:**
   - `C` → clear canvas
   - `S` → save canvas as PNG to Desktop (timestamped filename)

---

## SOURCE FILES (FULL CODE)

### `backend/handtracker.py` (Python AI Brain)

```python
import cv2
import mediapipe as mp
import socket
import time
import math

server_address = ('127.0.0.1', 5005)
sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

mp_hands    = mp.solutions.hands
mp_drawing  = mp.solutions.drawing_utils
hands       = mp_hands.Hands(max_num_hands=1, min_detection_confidence=0.7, min_tracking_confidence=0.7)

LANDMARK_STYLE   = mp_drawing.DrawingSpec(color=(0, 255, 255), thickness=2, circle_radius=3)
CONNECTION_STYLE = mp_drawing.DrawingSpec(color=(180, 255, 255), thickness=1)

cap = cv2.VideoCapture(0)
cap.set(cv2.CAP_PROP_FRAME_WIDTH,  640)
cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)

EMA_ALPHA = 0.25
DEADZONE  = 6

smooth_x, smooth_y       = 0.0, 0.0
prev_sent_x, prev_sent_y = -1, -1
prev_gesture              = ""
first_frame               = True

fps_timer   = time.time()
fps         = 0
frame_count = 0

def get_spread_pct(landmarks):
    i_tip = landmarks.landmark[8]
    p_tip = landmarks.landmark[20]
    wrist = landmarks.landmark[0]
    i_mcp = landmarks.landmark[5]
    spread = math.hypot(i_tip.x - p_tip.x, i_tip.y - p_tip.y)
    ref    = math.hypot(wrist.x - i_mcp.x, wrist.y - i_mcp.y)
    return min(100, int((spread / max(ref * 3.0, 0.001)) * 100))

while cap.isOpened():
    success, image = cap.read()
    if not success:
        continue

    frame_count += 1
    now = time.time()
    if now - fps_timer >= 1.0:
        fps = frame_count; frame_count = 0; fps_timer = now

    image   = cv2.flip(image, 1)
    h, w    = image.shape[:2]
    results = hands.process(cv2.cvtColor(image, cv2.COLOR_BGR2RGB))

    hands_detected = 0
    gesture        = "HOVER"
    spread_pct     = 0

    if results.multi_hand_landmarks:
        for hand_lm in results.multi_hand_landmarks:
            hands_detected += 1
            mp_drawing.draw_landmarks(image, hand_lm, mp_hands.HAND_CONNECTIONS, LANDMARK_STYLE, CONNECTION_STYLE)

            index_up  = hand_lm.landmark[8].y  < hand_lm.landmark[6].y
            middle_up = hand_lm.landmark[12].y < hand_lm.landmark[10].y
            ring_up   = hand_lm.landmark[16].y < hand_lm.landmark[14].y
            gesture   = "DRAW"  if (index_up and not middle_up) else \
                        "ERASE" if (index_up and middle_up and ring_up) else "HOVER"

            spread_pct = get_spread_pct(hand_lm)

            raw_x = hand_lm.landmark[8].x * 1280
            raw_y = hand_lm.landmark[8].y * 720
            if first_frame:
                smooth_x, smooth_y = raw_x, raw_y; first_frame = False
            else:
                smooth_x += EMA_ALPHA * (raw_x - smooth_x)
                smooth_y += EMA_ALPHA * (raw_y - smooth_y)
            x, y = int(smooth_x), int(smooth_y)

            moved     = abs(x - prev_sent_x) > DEADZONE or abs(y - prev_sent_y) > DEADZONE
            g_changed = gesture != prev_gesture
            if moved or prev_sent_x == -1 or g_changed:
                sock.sendto(f"{x},{y},{gesture}".encode(), server_address)
                prev_sent_x, prev_sent_y = x, y
                prev_gesture = gesture

            cmap  = {"DRAW": (60, 80, 255), "ERASE": (255, 255, 255), "HOVER": (0, 255, 140)}
            cx, cy = int(smooth_x * w / 1280), int(smooth_y * h / 720)
            cv2.circle(image, (cx, cy), 12, cmap.get(gesture, (0, 255, 0)), 2)
            cv2.circle(image, (cx, cy),  4, (255, 255, 255), -1)

    font = cv2.FONT_HERSHEY_SIMPLEX
    cv2.putText(image, f"Hands: {hands_detected}", (10, 22), font, 0.5, (0, 255, 180), 1)
    cv2.putText(image, f"FPS:   {fps:02d}",         (10, 42), font, 0.5, (160, 160, 160), 1)
    cv2.putText(image, f"Gesture: {gesture}",        (10, 62), font, 0.5, (0, 210, 180), 1)
    cv2.putText(image, f"Spread:  {spread_pct}%",    (10, 82), font, 0.5, (160, 160, 160), 1)

    cv2.imshow("Air Canvas - AI Tracker", image)
    if cv2.waitKey(1) & 0xFF == 27:
        break

cap.release()
cv2.destroyAllWindows()
```

---

### `src/main/java/com/aicanvas/App.java`

```java
package com.aicanvas;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class App {
    public static void main(String[] args) {
        System.out.println("Starting Air Canvas AI UI...");

        CoordinateReceiver receiver = new CoordinateReceiver();
        Thread t = new Thread(receiver);
        t.setDaemon(true);
        t.start();

        JFrame frame = new JFrame("Air Canvas AI");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1280, 720);

        DrawingCanvas canvas = new DrawingCanvas(1280, 720);
        frame.add(canvas);
        frame.setVisible(true);

        InputMap  im = canvas.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = canvas.getActionMap();

        im.put(KeyStroke.getKeyStroke('c'), "clear");
        im.put(KeyStroke.getKeyStroke('C'), "clear");
        am.put("clear", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { canvas.clearCanvas(); }
        });

        im.put(KeyStroke.getKeyStroke('s'), "save");
        im.put(KeyStroke.getKeyStroke('S'), "save");
        am.put("save", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                try {
                    String ts  = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
                    File   out = new File(System.getProperty("user.home") + "/Desktop/AirCanvas_" + ts + ".png");
                    ImageIO.write(canvas.getCanvasImage(), "PNG", out);
                    JOptionPane.showMessageDialog(frame, "Saved to Desktop!\n" + out.getName(), "Saved", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) { System.err.println("Save error: " + ex.getMessage()); }
            }
        });

        Timer timer = new Timer(33, e -> {
            int x = receiver.getX(), y = receiver.getY();
            String cmd = receiver.getCommand();
            if (x > 0 && y > 0) canvas.updatePointer(x, y, cmd);
            else                 canvas.resetPointer();
        });
        timer.start();
    }
}
```

---

### `src/main/java/com/aicanvas/CoordinateReceiver.java`

```java
package com.aicanvas;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;

public class CoordinateReceiver implements Runnable {
    private volatile int    currentX       = 0;
    private volatile int    currentY       = 0;
    private volatile String currentCommand = "HOVER";

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(5005)) {
            socket.setSoTimeout(200);
            byte[] buffer = new byte[256];
            System.out.println("Java UDP Receiver is listening on port 5005...");
            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String[] data = new String(packet.getData(), 0, packet.getLength()).split(",");
                    if (data.length >= 2) {
                        currentX = Integer.parseInt(data[0].trim());
                        currentY = Integer.parseInt(data[1].trim());
                        if (data.length >= 3) currentCommand = data[2].trim();
                    }
                } catch (SocketTimeoutException e) {
                    currentX = 0; currentY = 0; currentCommand = "HOVER";
                }
            }
        } catch (Exception e) { System.err.println("UDP Receiver error: " + e.getMessage()); }
    }

    public int    getX()       { return currentX; }
    public int    getY()       { return currentY; }
    public String getCommand() { return currentCommand; }
}
```

---

### `src/main/java/com/aicanvas/DrawingCanvas.java`

```java
package com.aicanvas;

import javax.swing.JPanel;
import java.awt.*;
import java.awt.image.BufferedImage;

final class Strokes {
    static final BasicStroke GLOW  = new BasicStroke(12, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    static final BasicStroke CORE  = new BasicStroke(4,  BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    static final BasicStroke ERASE = new BasicStroke(40, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    static final BasicStroke RING  = new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
}

public class DrawingCanvas extends JPanel {
    private final BufferedImage canvasImage;
    private final Graphics2D    g2d;
    private double cursorX = -1, cursorY = -1;
    private double lastDrawX = -1, lastDrawY = -1;
    private String currentMode = "HOVER";
    private static final double SMOOTHING = 0.15;
    private static final double MIN_MOVE  = 3.0;
    private float hue = 0f;
    private static final float HUE_STEP = 0.004f;
    private static final Composite GLOW_COMP = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f);
    private static final Composite FULL_COMP = AlphaComposite.SrcOver;
    private int  fpsDisplay = 0, frameCount = 0;
    private long fpsTimer   = System.currentTimeMillis();
    private static final Font  HUD_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final Color BG_COLOR = new Color(18, 18, 28);
    private static final Color HUD_CYAN = new Color(0, 220, 180);

    public DrawingCanvas(int width, int height) {
        canvasImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        g2d = canvasImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2d.setColor(BG_COLOR);
        g2d.fillRect(0, 0, width, height);
    }

    public void updatePointer(int tx, int ty, String command) {
        currentMode = command;
        if (cursorX < 0) { cursorX = tx; cursorY = ty; }
        else { cursorX += (tx - cursorX) * SMOOTHING; cursorY += (ty - cursorY) * SMOOTHING; }
        switch (command) {
            case "DRAW":
                if (lastDrawX >= 0) {
                    if (Math.hypot(cursorX - lastDrawX, cursorY - lastDrawY) >= MIN_MOVE) {
                        hue = (hue + HUE_STEP) % 1f;
                        drawNeon((int)lastDrawX,(int)lastDrawY,(int)cursorX,(int)cursorY, hue);
                        lastDrawX = cursorX; lastDrawY = cursorY;
                    }
                } else { lastDrawX = cursorX; lastDrawY = cursorY; }
                break;
            case "ERASE":
                if (lastDrawX >= 0) {
                    g2d.setComposite(FULL_COMP); g2d.setColor(BG_COLOR); g2d.setStroke(Strokes.ERASE);
                    g2d.drawLine((int)lastDrawX,(int)lastDrawY,(int)cursorX,(int)cursorY);
                }
                lastDrawX = cursorX; lastDrawY = cursorY;
                break;
            default: lastDrawX = -1; lastDrawY = -1; break;
        }
        repaint();
    }

    private void drawNeon(int x1, int y1, int x2, int y2, float h) {
        Color core = Color.getHSBColor(h, 1.0f, 1.0f);
        Color glow = Color.getHSBColor(h, 0.5f, 1.0f);
        g2d.setComposite(GLOW_COMP); g2d.setColor(glow); g2d.setStroke(Strokes.GLOW); g2d.drawLine(x1,y1,x2,y2);
        g2d.setComposite(FULL_COMP); g2d.setColor(core); g2d.setStroke(Strokes.CORE); g2d.drawLine(x1,y1,x2,y2);
    }

    public void resetPointer() { cursorX=-1; cursorY=-1; lastDrawX=-1; lastDrawY=-1; currentMode="HOVER"; repaint(); }
    public void clearCanvas()  { g2d.setColor(BG_COLOR); g2d.fillRect(0,0,canvasImage.getWidth(),canvasImage.getHeight()); repaint(); }
    public BufferedImage getCanvasImage() { return canvasImage; }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        frameCount++;
        long now = System.currentTimeMillis();
        if (now - fpsTimer >= 1000) { fpsDisplay = frameCount; frameCount = 0; fpsTimer = now; }
        g.drawImage(canvasImage, 0, 0, null);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (cursorX >= 0) {
            int cx=(int)cursorX, cy=(int)cursorY;
            if ("ERASE".equals(currentMode)) { g2.setStroke(Strokes.RING); g2.setColor(Color.WHITE); g2.drawOval(cx-20,cy-20,40,40); }
            else {
                Color cc = "DRAW".equals(currentMode) ? Color.getHSBColor(hue,1f,1f) : HUD_CYAN;
                g2.setColor(cc); g2.fillOval(cx-5,cy-5,10,10);
                g2.setColor(Color.WHITE); g2.fillOval(cx-2,cy-2,4,4);
            }
        }
        g2.setFont(HUD_FONT); g2.setColor(HUD_CYAN);
        g2.drawString("Hand:    " + (cursorX >= 0 ? "Active" : "None"), 14, 22);
        g2.drawString(String.format("FPS:     %02d", fpsDisplay), 14, 38);
        g2.setColor("DRAW".equals(currentMode)?Color.getHSBColor(hue,1f,1f):"ERASE".equals(currentMode)?Color.WHITE:HUD_CYAN);
        g2.drawString("Gesture: " + currentMode, 14, 54);
        g2.drawString("C = clear  |  S = save", 14, 70);
    }
}
```

---

## WHAT TO ASK CLAUDE TO MAKE

### For the Report, ask Claude:
> "Using the technical brief above, write a formal project report for a Java course evaluation. Include: Title Page, Abstract, Introduction, Problem Statement, Objectives, System Architecture, Technology Stack, Implementation Details (explain each class and method), Gesture Logic, Algorithm (EMA smoothing, deadzone filter), Key Challenges & Solutions, Results, Future Scope, and Conclusion."

### For the PPT, ask Claude:
> "Using the technical brief above, write slide-by-slide content for a 12-15 slide PowerPoint presentation for a Java course evaluation. The PPT should cover: project scope, architecture, tech stack, gesture system, key features/innovations, implementation highlights, demo flow, and conclusion. For each slide give: slide title, bullet points, and any speaker notes."

---

## QUICK FACTS FOR THE REPORT

- **Language:** Java 8 (Canvas/UI) + Python 3.12 (AI Brain)
- **Protocol:** UDP (User Datagram Protocol) on localhost:5005
- **Canvas resolution:** 1280 × 720 pixels
- **Frame rate:** ~30 FPS (Java timer: 33ms interval)
- **Smoothing alpha:** 0.25 (Python EMA)
- **Java smoothing factor:** 0.15 (exponential weighted average)
- **Deadzone:** 6px (Python, prevents unnecessary UDP sends) + 3px (Java, prevents micro-tremor marks)
- **Stroke rendering:** 2-layer AlphaComposite (15% alpha glow + 100% vivid core)
- **Memory:** TYPE_INT_RGB BufferedImage (24-bit, no wasted alpha channel)
- **GitHub:** https://github.com/Luciii-v/air-canvas-ai
