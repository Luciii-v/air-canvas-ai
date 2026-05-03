package com.aicanvas;

import javax.swing.JPanel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DrawingCanvas extends JPanel {

    private final BufferedImage staticCanvas; 
    private final Graphics2D    gStatic;

    private double cursorX = -1, cursorY = -1;
    private double lastDrawX = -1, lastDrawY = -1;
    private double midX = -1, midY = -1; 
    private String currentMode = "HOVER";
    private double currentThickness = 1.0;
    private Point[] currentLandmarks = new Point[21];
    
    private long lastTime = System.currentTimeMillis();
    private double velocity = 0;

    private static final double SMOOTHING = 0.6; 
    private static final double MIN_MOVE  = 2.0;
    private static final long   DECAY_MS  = 10000;

    private static final Color[] SUNSET_PALETTE = {
        new Color(255, 126, 95), new Color(254, 180, 123), 
        new Color(180, 58, 175), new Color(91, 22, 176)
    };
    private float palettePos = 0f;
    private static final float PALETTE_STEP = 0.005f;

    private static final Color BG_DARK      = new Color(8, 8, 12);
    private static final Color PANEL_GLASS  = new Color(25, 25, 35, 180);
    private static final Color BORDER_GLASS = new Color(255, 255, 255, 30);
    private static final Font  HUD_FONT_LG  = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    private static final Font  HUD_FONT_SM  = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

    private int  fpsDisplay = 0, frameCount = 0;
    private long fpsTimer   = System.currentTimeMillis();
    private final Random random = new Random();

    // Export Flash
    private long flashTime = 0;
    private static final long FLASH_DURATION = 600;
    private static final Color FLASH_FIRE = new Color(255, 100, 20, 200);

    // Hellfire Particles
    private static class FireParticle {
        double x, y, vx, vy;
        float life = 1.0f;
        Color color;
        FireParticle(double x, double y, double vx, double vy, Color c) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.color = c;
        }
    }
    private final List<FireParticle> fireParticles = new ArrayList<>();

    private static class LightStroke {
        Path2D.Double path;
        Color color;
        float thickness;
        long timestamp;
        boolean isParticle;
        List<Point.Double> particles;

        LightStroke(Path2D.Double path, Color color, float thickness, boolean isParticle) {
            this.path = path;
            this.color = color;
            this.thickness = thickness;
            this.timestamp = System.currentTimeMillis();
            this.isParticle = isParticle;
            if (isParticle) particles = new ArrayList<>();
        }
    }
    private final List<LightStroke> strokes = new ArrayList<>();

    public DrawingCanvas(int width, int height) {
        staticCanvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        gStatic = staticCanvas.createGraphics();
        gStatic.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gStatic.setColor(BG_DARK);
        gStatic.fillRect(0, 0, width, height);
        for(int i=0; i<21; i++) currentLandmarks[i] = new Point(0,0);
    }

    public void updatePointer(int tx, int ty, String command, double thickness, Point[] lms) {
        currentMode = command;
        currentThickness = thickness;
        for(int i=0; i<21; i++) {
            currentLandmarks[i].x = lms[i].x;
            currentLandmarks[i].y = lms[i].y;
            // Spawn fire particles from knuckles
            if (lms[i].x > 0) spawnFire(lms[i].x, lms[i].y);
        }

        long now = System.currentTimeMillis();
        long dt = now - lastTime;
        if (dt > 0) {
            double dist = Math.hypot(tx - cursorX, ty - cursorY);
            velocity = dist / dt;
        }
        lastTime = now;
        
        if (cursorX < 0) { cursorX = tx; cursorY = ty; midX = tx; midY = ty; } 
        else { cursorX += (tx - cursorX) * SMOOTHING; cursorY += (ty - cursorY) * SMOOTHING; }

        switch (command) {
            case "DRAW":
                if (lastDrawX >= 0) {
                    if (Math.hypot(cursorX - lastDrawX, cursorY - lastDrawY) >= MIN_MOVE) {
                        palettePos = (palettePos + PALETTE_STEP) % 1.0f;
                        double nx = (lastDrawX + cursorX) / 2; double ny = (lastDrawY + cursorY) / 2;
                        addLightPaintingStroke((int)midX, (int)midY, (int)lastDrawX, (int)lastDrawY, (int)nx, (int)ny, palettePos, thickness);
                        midX = nx; midY = ny; lastDrawX = cursorX; lastDrawY = cursorY;
                    }
                } else { lastDrawX = cursorX; lastDrawY = cursorY; midX = cursorX; midY = cursorY; }
                break;
            case "ERASE":
                if (lastDrawX >= 0) {
                    gStatic.setComposite(AlphaComposite.SrcOver); 
                    gStatic.setColor(BG_DARK);
                    gStatic.setStroke(new BasicStroke((float)(60 * thickness), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    gStatic.drawLine((int)lastDrawX, (int)lastDrawY, (int)cursorX, (int)cursorY);
                    clearNearbyStrokes(cursorX, cursorY, 60 * thickness);
                }
                lastDrawX = cursorX; lastDrawY = cursorY;
                break;
            default: lastDrawX = -1; lastDrawY = -1; break;
        }
        repaint();
    }

    private void spawnFire(int x, int y) {
        Color c = random.nextBoolean() ? new Color(255, 60, 0) : new Color(255, 150, 0);
        fireParticles.add(new FireParticle(x, y, (random.nextDouble()-0.5)*2, -random.nextDouble()*3, c));
    }

    public void triggerExport(DashboardUI dash) {
        flashTime = System.currentTimeMillis();
        try {
            BufferedImage exportImg = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = exportImg.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(staticCanvas, 0, 0, null);
            synchronized(strokes) {
                long now = System.currentTimeMillis();
                for (LightStroke s : strokes) {
                    float lifeFactor = 1.0f - ((float)(now - s.timestamp) / DECAY_MS);
                    if (lifeFactor > 0) renderLightStroke(g, s, lifeFactor);
                }
            }
            g.dispose();
            String ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
            File out = new File(System.getProperty("user.home") + "/Desktop/Masterpiece_" + ts + ".png");
            ImageIO.write(exportImg, "PNG", out);
            dash.showSavedBadge();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void addLightPaintingStroke(int x1, int y1, int cx, int cy, int x2, int y2, float pos, double thick) {
        Color base = getSunsetLevel(pos);
        Path2D.Double path = new Path2D.Double();
        path.moveTo(x1, y1); path.quadTo(cx, cy, x2, y2);
        float vThick = (float)(thick * (1.5 / (velocity + 0.5)));
        boolean isFast = velocity > 1.2;
        LightStroke stroke = new LightStroke(path, base, vThick, isFast);
        if (isFast) {
            for (int i = 0; i < 5; i++) {
                double t = random.nextDouble();
                double px = (1-t)*(1-t)*x1 + 2*(1-t)*t*cx + t*t*x2 + (random.nextGaussian() * 10);
                double py = (1-t)*(1-t)*y1 + 2*(1-t)*t*cy + t*t*y2 + (random.nextGaussian() * 10);
                stroke.particles.add(new Point.Double(px, py));
            }
        }
        synchronized(strokes) { strokes.add(stroke); }
    }

    private void clearNearbyStrokes(double x, double y, double radius) {
        synchronized(strokes) {
            Iterator<LightStroke> it = strokes.iterator();
            while(it.hasNext()) {
                LightStroke s = it.next();
                if (s.path.getBounds2D().intersects(x-radius, y-radius, radius*2, radius*2)) it.remove();
            }
        }
    }

    private Color getSunsetLevel(float pos) {
        int sections = SUNSET_PALETTE.length - 1; float scaled = pos * sections;
        int idx = (int) scaled; float factor = scaled - idx;
        Color c1 = SUNSET_PALETTE[idx]; Color c2 = SUNSET_PALETTE[Math.min(idx + 1, sections)];
        return new Color((int)(c1.getRed()+(c2.getRed()-c1.getRed())*factor), (int)(c1.getGreen()+(c2.getGreen()-c1.getGreen())*factor), (int)(c1.getBlue()+(c2.getBlue()-c1.getBlue())*factor));
    }

    public void resetPointer() {
        cursorX = -1; cursorY = -1; lastDrawX = -1; lastDrawY = -1; midX = -1; midY = -1;
        currentMode = "HOVER"; repaint();
    }

    public void clearCanvas() { 
        gStatic.setColor(BG_DARK); gStatic.fillRect(0, 0, staticCanvas.getWidth(), staticCanvas.getHeight()); 
        synchronized(strokes) { strokes.clear(); }
        repaint(); 
    }
    
    public int getFPS() { return fpsDisplay; }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        frameCount++; long now = System.currentTimeMillis();
        if (now - fpsTimer >= 1000) { fpsDisplay = frameCount; frameCount = 0; fpsTimer = now; }

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(staticCanvas, 0, 0, null);

        synchronized(strokes) {
            Iterator<LightStroke> it = strokes.iterator();
            while(it.hasNext()) {
                LightStroke s = it.next();
                long age = now - s.timestamp;
                if (age > DECAY_MS) { it.remove(); continue; }
                renderLightStroke(g2, s, 1.0f - ((float)age / DECAY_MS));
            }
        }

        // Hellfire Skeleton
        if (cursorX >= 0) {
            updateAndRenderFire(g2);
            drawHellfireSkeleton(g2);
        }

        drawHUD(g2);

        // Fiery Flash Effect
        if (now - flashTime < FLASH_DURATION) {
            float alpha = 1.0f - ((float)(now - flashTime) / FLASH_DURATION);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.9f));
            g2.setColor(FLASH_FIRE);
            g2.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    private void updateAndRenderFire(Graphics2D g2) {
        Iterator<FireParticle> it = fireParticles.iterator();
        while (it.hasNext()) {
            FireParticle p = it.next();
            p.life -= 0.05f;
            if (p.life <= 0) { it.remove(); continue; }
            p.x += p.vx; p.y += p.vy;
            int alpha = (int)(255 * p.life);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, p.life * 0.4f));
            g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), alpha));
            int size = (int)(5 * p.life);
            g2.fillOval((int)p.x, (int)p.y, size, size);
        }
    }

    private void drawHellfireSkeleton(Graphics2D g2) {
        int[][] connections = {{0,1,2,3,4}, {0,5,6,7,8}, {5,9,10,11,12}, {9,13,14,15,16}, {13,17,18,19,20}, {0,17}};
        Color auraColor = new Color(255, 30, 0, 180);
        Color midColor = new Color(255, 120, 0, 220);
        
        for (int[] chain : connections) {
            for (int j = 0; j < chain.length - 1; j++) {
                Point p1 = currentLandmarks[chain[j]]; Point p2 = currentLandmarks[chain[j+1]];
                if (p1.x > 0 && p2.x > 0) {
                    // Pass 1: Crimson Aura (Pulsing)
                    float pulse = (float)(Math.sin(System.currentTimeMillis() * 0.01) * 0.2 + 0.8);
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f * pulse));
                    g2.setColor(auraColor);
                    g2.setStroke(new BasicStroke(12, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine(p1.x, p1.y, p2.x, p2.y);
                    
                    // Pass 2: Orange Inner Fire
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
                    g2.setColor(midColor);
                    g2.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine(p1.x, p1.y, p2.x, p2.y);
                    
                    // Pass 3: White-hot Core
                    g2.setComposite(AlphaComposite.SrcOver);
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine(p1.x, p1.y, p2.x, p2.y);
                }
            }
        }
        
        // Knuckle Joint Nodes
        for (Point p : currentLandmarks) {
            if (p.x > 0) {
                g2.setColor(Color.WHITE);
                g2.fillOval(p.x - 3, p.y - 3, 6, 6);
                g2.setStroke(new BasicStroke(1));
                g2.setColor(midColor);
                g2.drawOval(p.x - 6, p.y - 6, 12, 12);
            }
        }
    }

    private void renderLightStroke(Graphics2D g2, LightStroke s, float life) {
        Color c = s.color;
        int alpha = (int)(255 * life);
        if (alpha <= 0) return;
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, life * 0.15f));
        g2.setColor(c);
        g2.setStroke(new BasicStroke(s.thickness * 15f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(s.path);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, life));
        g2.setStroke(new BasicStroke(s.thickness * 4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha));
        g2.draw(s.path);
        if (s.isParticle && s.particles != null) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, life * 0.6f));
            for (Point.Double p : s.particles) {
                int pSize = random.nextInt(3) + 1;
                g2.fillOval((int)p.x, (int)p.y, pSize, pSize);
            }
        }
    }

    private void drawHUD(Graphics2D g2) {
        drawGlassPanel(g2, 15, 15, 200, 100);
        g2.setFont(HUD_FONT_LG); g2.setColor(Color.WHITE); g2.drawString("AI CANVAS PRO", 30, 45);
        g2.setFont(HUD_FONT_SM); g2.setColor(new Color(200, 200, 220));
        g2.drawString("Status:  " + (cursorX >= 0 ? "● Active" : "○ Searching"), 30, 70);
        g2.drawString("FPS:     " + fpsDisplay, 30, 88);

        drawGlassPanel(g2, 15, 125, 200, 60);
        Color modeColor = "DRAW".equals(currentMode) ? getSunsetLevel(palettePos) : "ERASE".equals(currentMode) ? Color.WHITE : new Color(0, 255, 180);
        g2.setComposite(AlphaComposite.SrcOver);
        g2.setColor(modeColor); g2.fillOval(30, 140, 8, 8);
        g2.setColor(Color.WHITE); g2.drawString("MODE: " + currentMode, 45, 148);
        g2.drawString(String.format("SIZE: %.1f | VEL: %.1f", currentThickness, velocity), 45, 168);
    }

    private void drawGlassPanel(Graphics2D g2, int x, int y, int w, int h) {
        g2.setComposite(AlphaComposite.SrcOver); g2.setColor(PANEL_GLASS);
        g2.fillRoundRect(x, y, w, h, 15, 15);
        g2.setColor(BORDER_GLASS); g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(x, y, w, h, 15, 15);
    }
    public java.awt.image.BufferedImage getCanvasImage() {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
            this.getWidth(), this.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2d = image.createGraphics();
        this.paint(g2d);
        g2d.dispose();
        return image;
    }
}