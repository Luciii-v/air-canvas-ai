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

        // ── Keyboard Shortcuts (WHEN_IN_FOCUSED_WINDOW = no focus needed) ──
        InputMap  im = canvas.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = canvas.getActionMap();

        // C / c  →  Clear canvas
        im.put(KeyStroke.getKeyStroke('c'), "clear");
        im.put(KeyStroke.getKeyStroke('C'), "clear");
        am.put("clear", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                canvas.clearCanvas();
                System.out.println("Canvas cleared.");
            }
        });

        // S / s  →  Save PNG to Desktop
        im.put(KeyStroke.getKeyStroke('s'), "save");
        im.put(KeyStroke.getKeyStroke('S'), "save");
        am.put("save", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                try {
                    String ts  = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                                                  .format(LocalDateTime.now());
                    File   out = new File(System.getProperty("user.home")
                                         + "/Desktop/AirCanvas_" + ts + ".png");
                    ImageIO.write(canvas.getCanvasImage(), "PNG", out);
                    System.out.println("Saved → " + out.getAbsolutePath());
                    JOptionPane.showMessageDialog(frame,
                        "Saved to Desktop!\n" + out.getName(),
                        "Canvas Saved ✔", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    System.err.println("Save error: " + ex.getMessage());
                }
            }
        });

        // ── 30 FPS game loop ──────────────────────────────────────────────
        Timer timer = new Timer(33, e -> {
            int    x   = receiver.getX();
            int    y   = receiver.getY();
            String cmd = receiver.getCommand();

            if (x > 0 && y > 0) canvas.updatePointer(x, y, cmd);
            else                 canvas.resetPointer();
        });
        timer.start();
    }
}