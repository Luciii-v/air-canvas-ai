package com.aicanvas;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class DashboardUI {

    private Stage stage;
    private Label modeLabel;
    private Label fpsLabel;
    private Label savedBadge;
    private ProgressBar thicknessBar;
    private VBox root;

    private String lastMode = "";

    public void start(Stage owner) {
        stage = new Stage();
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.initOwner(owner);

        root = new VBox(15);
        root.setPadding(new Insets(25));
        root.setPrefSize(240, 380);
        root.setBackground(new Background(new BackgroundFill(
                new Color(0.05, 0.05, 0.07, 0.85), new CornerRadii(20), Insets.EMPTY)));
        root.setBorder(new Border(new BorderStroke(
                new Color(1, 1, 1, 0.1), BorderStrokeStyle.SOLID, new CornerRadii(20), new BorderWidths(1.5))));

        // Title
        Label title = new Label("SYSTEM TELEMETRY");
        title.setFont(Font.font("Inter", FontWeight.BOLD, 12));
        title.setTextFill(new Color(0.6, 0.6, 0.7, 1.0));
        title.setOpacity(0.8);

        // Mode Section
        VBox modeBox = new VBox(5);
        Label modeTitle = new Label("ACTIVE MODE");
        modeTitle.setFont(Font.font("Inter", FontWeight.SEMI_BOLD, 10));
        modeTitle.setTextFill(new Color(0.5, 0.5, 0.6, 1.0));
        
        modeLabel = new Label("INITIALIZING");
        modeLabel.setFont(Font.font("Inter", FontWeight.BLACK, 24));
        modeLabel.setTextFill(Color.WHITE);
        modeBox.getChildren().addAll(modeTitle, modeLabel);

        // Saved Badge
        savedBadge = new Label("MASTERPIECE SAVED! ✔");
        savedBadge.setFont(Font.font("Inter", FontWeight.BOLD, 12));
        savedBadge.setTextFill(new Color(0.2, 1.0, 0.5, 1.0));
        savedBadge.setOpacity(0);
        savedBadge.setPadding(new Insets(5, 10, 5, 10));
        savedBadge.setBackground(new Background(new BackgroundFill(new Color(0.2, 1.0, 0.5, 0.1), new CornerRadii(5), Insets.EMPTY)));

        // Thickness Section
        VBox thickBox = new VBox(8);
        Label thickTitle = new Label("BRUSH THICKNESS");
        thickTitle.setFont(Font.font("Inter", FontWeight.SEMI_BOLD, 10));
        thickTitle.setTextFill(new Color(0.5, 0.5, 0.6, 1.0));
        
        thicknessBar = new ProgressBar(0.5);
        thicknessBar.setPrefWidth(190);
        thicknessBar.setStyle("-fx-accent: #FF7E5F; -fx-control-inner-background: rgba(255,255,255,0.05); -fx-background-color: transparent;");
        thickBox.getChildren().addAll(thickTitle, thicknessBar);

        // Stats Section
        HBox statsBox = new HBox(20);
        VBox fpsBox = new VBox(2);
        Label fpsTitle = new Label("PERFORMANCE");
        fpsTitle.setFont(Font.font("Inter", FontWeight.SEMI_BOLD, 10));
        fpsTitle.setTextFill(new Color(0.5, 0.5, 0.6, 1.0));
        fpsLabel = new Label("00 FPS");
        fpsLabel.setFont(Font.font("Inter", FontWeight.MEDIUM, 16));
        fpsLabel.setTextFill(new Color(0.0, 0.8, 1.0, 1.0));
        fpsBox.getChildren().addAll(fpsTitle, fpsLabel);
        statsBox.getChildren().add(fpsBox);

        root.getChildren().addAll(title, modeBox, savedBadge, thickBox, statsBox);
        root.setAlignment(Pos.TOP_LEFT);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);
        
        stage.setX(owner.getX() + owner.getWidth() + 10);
        stage.setY(owner.getY());
        stage.show();
    }

    public void update(String mode, double thickness, int fps) {
        Platform.runLater(() -> {
            if (!mode.equals(lastMode) && !mode.equals("EXPORT")) {
                animateChange();
                modeLabel.setText(mode.toUpperCase());
                lastMode = mode;
                
                if (mode.equals("DRAW")) modeLabel.setTextFill(new Color(1.0, 0.49, 0.37, 1.0));
                else if (mode.equals("ERASE")) modeLabel.setTextFill(Color.WHITE);
                else modeLabel.setTextFill(new Color(0.0, 0.86, 0.7, 1.0));
            }
            
            thicknessBar.setProgress(thickness / 2.0);
            fpsLabel.setText(String.format("%02d FPS", fps));
        });
    }

    public void showSavedBadge() {
        Platform.runLater(() -> {
            FadeTransition ft = new FadeTransition(Duration.millis(300), savedBadge);
            ft.setFromValue(0);
            ft.setToValue(1.0);
            ft.setAutoReverse(true);
            ft.setCycleCount(2);
            ft.setDelay(Duration.seconds(0));
            ft.setOnFinished(e -> savedBadge.setOpacity(0));
            
            // Stay visible for 2 seconds
            FadeTransition stay = new FadeTransition(Duration.seconds(2), savedBadge);
            stay.setFromValue(1.0);
            stay.setToValue(1.0);
            
            FadeTransition out = new FadeTransition(Duration.millis(500), savedBadge);
            out.setFromValue(1.0);
            out.setToValue(0);

            javafx.animation.SequentialTransition seq = new javafx.animation.SequentialTransition(ft, stay, out);
            seq.play();
        });
    }

    private void animateChange() {
        FadeTransition ft = new FadeTransition(Duration.millis(300), modeLabel);
        ft.setFromValue(0.4);
        ft.setToValue(1.0);
        ft.play();
    }
}