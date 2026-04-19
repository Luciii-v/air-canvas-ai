package com.aicanvas;

import javax.swing.JPanel;
import java.awt.*;
import java.awt.image.BufferedImage;

// All strokes pre-allocated — never new'd inside the draw loop
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

    // Rainbow hue — cheap: just float arithmetic
    private float hue = 0f;
    private static final float HUE_STEP = 0.004f;

    // Pre-allocated composites — no object creation per frame
    private static final Composite GLOW_COMP = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f);
    private static final Composite FULL_COMP = AlphaComposite.SrcOver;

    // HUD
    private int  fpsDisplay = 0, frameCount = 0;
    private long fpsTimer   = System.currentTimeMillis();
    private static final Font  HUD_FONT  = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final Color BG_COLOR  = new Color(18, 18, 28);
    private static final Color HUD_CYAN  = new Color(0, 220, 180);

    public DrawingCanvas(int width, int height) {
        canvasImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        g2d = canvasImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,   RenderingHints.VALUE_ANTIALIAS_ON);
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
                    g2d.setComposite(FULL_COMP);
                    g2d.setColor(BG_COLOR); g2d.setStroke(Strokes.ERASE);
                    g2d.drawLine((int)lastDrawX,(int)lastDrawY,(int)cursorX,(int)cursorY);
                }
                lastDrawX = cursorX; lastDrawY = cursorY;
                break;
            default: lastDrawX = -1; lastDrawY = -1; break;
        }
        repaint();
    }

    /** 2-layer neon: thin glow halo + vivid solid core. Fast, zero allocation. */
    private void drawNeon(int x1, int y1, int x2, int y2, float h) {
        Color core = Color.getHSBColor(h, 1.0f, 1.0f);
        Color glow = Color.getHSBColor(h, 0.5f, 1.0f);

        g2d.setComposite(GLOW_COMP);
        g2d.setColor(glow); g2d.setStroke(Strokes.GLOW);
        g2d.drawLine(x1, y1, x2, y2);

        g2d.setComposite(FULL_COMP);
        g2d.setColor(core); g2d.setStroke(Strokes.CORE);
        g2d.drawLine(x1, y1, x2, y2);
    }

    public void resetPointer() {
        cursorX = -1; cursorY = -1; lastDrawX = -1; lastDrawY = -1;
        currentMode = "HOVER"; repaint();
    }

    public void clearCanvas() {
        g2d.setColor(BG_COLOR);
        g2d.fillRect(0, 0, canvasImage.getWidth(), canvasImage.getHeight());
        repaint();
    }

    public BufferedImage getCanvasImage() { return canvasImage; }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // FPS tracking
        frameCount++;
        long now = System.currentTimeMillis();
        if (now - fpsTimer >= 1000) { fpsDisplay = frameCount; frameCount = 0; fpsTimer = now; }

        g.drawImage(canvasImage, 0, 0, null);

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Cursor
        if (cursorX >= 0) {
            int cx = (int)cursorX, cy = (int)cursorY;
            if ("ERASE".equals(currentMode)) {
                g2.setStroke(Strokes.RING); g2.setColor(Color.WHITE);
                g2.drawOval(cx-20, cy-20, 40, 40);
            } else {
                Color cc = "DRAW".equals(currentMode) ? Color.getHSBColor(hue,1f,1f) : HUD_CYAN;
                g2.setColor(cc); g2.fillOval(cx-5, cy-5, 10, 10);
                g2.setColor(Color.WHITE); g2.fillOval(cx-2, cy-2, 4, 4);
            }
        }

        // Minimal HUD — plain text only, no compositing
        g2.setFont(HUD_FONT);
        g2.setColor(HUD_CYAN);
        g2.drawString("Hand:    " + (cursorX >= 0 ? "Active" : "None"), 14, 22);
        g2.drawString(String.format("FPS:     %02d", fpsDisplay),        14, 38);
        g2.setColor("DRAW".equals(currentMode) ? Color.getHSBColor(hue,1f,1f)
                  : "ERASE".equals(currentMode) ? Color.WHITE : HUD_CYAN);
        g2.drawString("Gesture: " + currentMode, 14, 54);
        g2.drawString("C = clear  |  S = save", 14, 70);
    }
}