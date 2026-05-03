package com.aicanvas;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.embed.swing.JFXPanel;

public class App {
    public static void main(String[] args) {
        System.out.println("Starting AI Canvas Pro...");

        // Initialize JavaFX Platform
        new JFXPanel(); 

        CoordinateReceiver receiver = new CoordinateReceiver();
        Thread t = new Thread(receiver);
        t.setDaemon(true);
        t.start();

        JFrame frame = new JFrame("AI Canvas Pro - Cinematic Edition");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1280, 720);

        DrawingCanvas canvas = new DrawingCanvas(1280, 720);
        frame.add(canvas);
        frame.setVisible(true);

        // Start Dashboard UI
        DashboardUI dashboard = new DashboardUI();
        Platform.runLater(() -> {
            Stage dummy = new Stage(); 
            dashboard.start(dummy);
        });

        // ── Keyboard Shortcuts ──
        InputMap  im = canvas.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = canvas.getActionMap();

        im.put(KeyStroke.getKeyStroke('c'), "clear");
        im.put(KeyStroke.getKeyStroke('C'), "clear");
        am.put("clear", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                canvas.clearCanvas();
            }
        });

        im.put(KeyStroke.getKeyStroke('s'), "save");
        im.put(KeyStroke.getKeyStroke('S'), "save");
        am.put("save", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                try {
                    String ts  = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
                    File   out = new File(System.getProperty("user.home") + "/Desktop/AirCanvas_" + ts + ".png");
                    ImageIO.write(canvas.getCanvasImage(), "PNG", out);
                    JOptionPane.showMessageDialog(frame, "Saved to Desktop!\n" + out.getName());
                } catch (Exception ex) { ex.printStackTrace(); }
            }
        });

        // ── 30 FPS game loop ──────────────────────────────────────────────
        Timer timer = new Timer(33, e -> {
            int    x   = receiver.getX();
            int    y   = receiver.getY();
            String cmd = receiver.getCommand();
            double thick = receiver.getThickness();
            Point[] lms = receiver.getLandmarks();

            if ("EXPORT".equals(cmd)) {
                canvas.triggerExport(dashboard);
            }

            if (x > 0 && y > 0) canvas.updatePointer(x, y, cmd, thick, lms);
            else                 canvas.resetPointer();

            // Update Dashboard
            dashboard.update(cmd, thick, canvas.getFPS());
        });
        timer.start();
    }
}