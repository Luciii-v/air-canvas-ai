package com.aicanvas;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

import org.opencv.highgui.HighGui;
import nu.pattern.OpenCV;
import java.util.ArrayList;
import java.util.List;

public class App {
    public static void main(String[] args) {
        OpenCV.loadLocally();
        System.out.println("OpenCV Loaded! Initializing Mac Webcam...");

        VideoCapture camera = new VideoCapture(0);
        if (!camera.isOpened()) {
            System.out.println("Error: Camera not found!");
            return;
        }

        Mat frame = new Mat();
        Mat hsv = new Mat();
        Mat mask = new Mat();
        Mat canvas = new Mat(); 
        Point lastPoint = new Point(0, 0);

        boolean isDrawing = true; // Start in draw mode by default

        System.out.println("AI Canvas - Virtual Smartboard Ready!");
        System.out.println("-> Use your highlighter to 'click' the buttons at the top of the screen.");

        while (true) {
            camera.read(frame);
            if (frame.empty()) continue;

            Core.flip(frame, frame, 1);
            
            if (canvas.empty()) {
                canvas = Mat.zeros(frame.size(), frame.type());
            }

            Imgproc.cvtColor(frame, hsv, Imgproc.COLOR_BGR2HSV);

            // Fluorescent Lighting Yellow Filter
            Scalar lowerBounds = new Scalar(20, 60, 60); 
            Scalar upperBounds = new Scalar(50, 255, 255);
            Core.inRange(hsv, lowerBounds, upperBounds, mask);

            Imgproc.erode(mask, mask, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5, 5)));
            Imgproc.dilate(mask, mask, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5, 5)));

            List<MatOfPoint> contours = new ArrayList<>();
            Mat hierarchy = new Mat();
            Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            boolean markerFound = false;
            Point penTip = new Point(0, 0);

            if (!contours.isEmpty()) {
                double maxArea = 0;
                int maxAreaIdx = -1;
                for (int i = 0; i < contours.size(); i++) {
                    double area = Imgproc.contourArea(contours.get(i));
                    if (area > maxArea) {
                        maxArea = area;
                        maxAreaIdx = i;
                    }
                }

                if (maxAreaIdx != -1 && maxArea > 50) {
                    markerFound = true;
                    MatOfPoint largestContour = contours.get(maxAreaIdx);
                    Point[] contourArray = largestContour.toArray();

                    penTip = contourArray[0];
                    for (Point p : contourArray) {
                        if (p.y < penTip.y) {
                            penTip = p;
                        }
                    }

                    // --- VIRTUAL BUTTON CLICK LOGIC ---
                    // If the marker tip enters the top 80 pixels of the screen...
                    if (penTip.y <= 80) {
                        if (penTip.x >= 20 && penTip.x <= 170) {
                            isDrawing = true; // Clicked DRAW
                        } else if (penTip.x >= 190 && penTip.x <= 340) {
                            isDrawing = false; // Clicked HOVER
                        } else if (penTip.x >= 360 && penTip.x <= 510) {
                            canvas.setTo(new Scalar(0, 0, 0)); // Clicked CLEAR
                        }
                    }

                    // --- DRAWING LOGIC ---
                    // Only draw if we are in Draw mode AND we are NOT currently touching the UI header
                    if (isDrawing && penTip.y > 80) {
                        Imgproc.circle(frame, penTip, 10, new Scalar(0, 255, 0), -1); 
                        if (lastPoint.x != 0 && lastPoint.y != 0) {
                            Imgproc.line(canvas, lastPoint, penTip, new Scalar(255, 0, 0), 12); 
                        }
                        lastPoint = penTip; 
                    } else {
                        // Hover mode OR currently touching a button
                        Imgproc.circle(frame, penTip, 10, new Scalar(0, 0, 255), -1); 
                        lastPoint = new Point(0, 0); 
                    }
                }
            }

            if (!markerFound) {
                lastPoint = new Point(0, 0);
            }

            Core.add(frame, canvas, frame);

            // --- DRAW THE VIRTUAL UI BUTTONS ---
            // 1. DRAW Button (Green)
            Imgproc.rectangle(frame, new Point(20, 10), new Point(170, 70), new Scalar(0, 255, 0), isDrawing ? -1 : 2);
            Imgproc.putText(frame, "DRAW", new Point(50, 50), Imgproc.FONT_HERSHEY_SIMPLEX, 0.8, isDrawing ? new Scalar(0,0,0) : new Scalar(0, 255, 0), 2);
            
            // 2. HOVER Button (Red)
            Imgproc.rectangle(frame, new Point(190, 10), new Point(340, 70), new Scalar(0, 0, 255), !isDrawing ? -1 : 2);
            Imgproc.putText(frame, "HOVER", new Point(215, 50), Imgproc.FONT_HERSHEY_SIMPLEX, 0.8, !isDrawing ? new Scalar(0,0,0) : new Scalar(0, 0, 255), 2);
            
            // 3. CLEAR Button (White)
            Imgproc.rectangle(frame, new Point(360, 10), new Point(510, 70), new Scalar(255, 255, 255), 2);
            Imgproc.putText(frame, "CLEAR", new Point(390, 50), Imgproc.FONT_HERSHEY_SIMPLEX, 0.8, new Scalar(255, 255, 255), 2);

            HighGui.imshow("AI Canvas - Virtual Smartboard", frame);

            if (HighGui.waitKey(1) == 27) { 
                break; 
            } 
        }

        camera.release();
        HighGui.destroyAllWindows();
        System.exit(0);
    }
}