package com.aicanvas;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.awt.Point;

public class CoordinateReceiver implements Runnable {

    private volatile String currentCommand = "HOVER";
    private volatile double currentThickness = 0.5;
    private volatile Point[] landmarks = new Point[21];

    public CoordinateReceiver() {
        for (int i = 0; i < 21; i++) landmarks[i] = new Point(0, 0);
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(5005)) {
            socket.setSoTimeout(200); 
            byte[] buffer = new byte[1024]; // Increased buffer for full landmarks

            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String raw = new String(packet.getData(), 0, packet.getLength());
                    String[] parts = raw.split("\\|");
                    
                    if (parts.length > 0) {
                        String[] header = parts[0].split(",");
                        if (header.length >= 1) currentCommand = header[0].trim();
                        if (header.length >= 2) currentThickness = Double.parseDouble(header[1].trim());
                    }

                    if (parts.length >= 22) {
                        for (int i = 0; i < 21; i++) {
                            String[] coords = parts[i + 1].split(",");
                            if (coords.length >= 2) {
                                landmarks[i].x = Integer.parseInt(coords[0].trim());
                                landmarks[i].y = Integer.parseInt(coords[1].trim());
                            }
                        }
                    }

                } catch (SocketTimeoutException e) {
                    currentCommand = "HOVER";
                    for (Point p : landmarks) { p.x = 0; p.y = 0; }
                } catch (Exception e) {
                    // Ignore malformed packets
                }
            }
        } catch (Exception e) {
            System.err.println("UDP Receiver error: " + e.getMessage());
        }
    }

    public int getX() { return landmarks[8].x; } // Index tip
    public int getY() { return landmarks[8].y; }
    public String getCommand() { return currentCommand; }
    public double getThickness() { return currentThickness; }
    public Point[] getLandmarks() { return landmarks; }
}