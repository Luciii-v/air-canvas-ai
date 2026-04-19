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
            socket.setSoTimeout(200); // resets state when hand leaves frame
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
        } catch (Exception e) {
            System.err.println("UDP Receiver error: " + e.getMessage());
        }
    }

    public int    getX()       { return currentX; }
    public int    getY()       { return currentY; }
    public String getCommand() { return currentCommand; }
}