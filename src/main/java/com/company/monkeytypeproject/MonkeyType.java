package com.company.monkeytypeproject;

import javafx.stage.Modality;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.Random;
import javafx.concurrent.Task;
import javafx.application.Platform;
import javafx.scene.image.*;
import javafx.scene.input.KeyCode;


public class MonkeyType extends Application {
    /**
     MODEL      *****************************************************************************************************
     */
    private long elapsedSeconds;
    private int correctChars = 0;
    private int incorrectChars = 0;
    private int extraChars = 0;
    private int missedChars = 0;
    private String testText;
    private long startTime;
    private boolean isTestActive;
    private int charIndex = 0;
    private Task<Void> typingTask;
    private Thread typingThread;

    /**
     VIEW       *********************************************************************************************************
     */
    private ComboBox<Integer> durationComboBox;
    private TextFlow textFlow;
    private TextField inputField, languageField;
    private Label typingLabel;
    private Button startButton;

    @Override
    public void start(Stage primaryStage) {
        initializeComponents();

        VBox layout = new VBox(10);
        layout.setStyle("-fx-background-color: #1e1e1e;");
        layout.getChildren().addAll(languageField, typingLabel, textFlow, inputField, startButton);
        durationComboBox = new ComboBox<>();
        durationComboBox.getItems().addAll(15, 20, 45, 60, 90, 120, 300);
        durationComboBox.setValue(60);
        layout.getChildren().add(1, durationComboBox);

        Scene scene = new Scene(layout, 800, 600);
        setupKeyCombinations(scene);

        Image commandImage = new Image("Images/img1.png");
        ImageView commandImageView = new ImageView(commandImage);
        commandImageView.setPreserveRatio(true);
        commandImageView.setFitWidth(300);
        layout.getChildren().add(commandImageView);

        primaryStage.setTitle("Monkey Type");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void initializeComponents() {
        textFlow = new TextFlow();
        inputField = new TextField();
        languageField = new TextField();
        typingLabel = new Label("Type below:");
        startButton = new Button("Start Test");
        textFlow.setStyle("-fx-padding: 20;");
        typingLabel.setTextFill(Color.web("#CCCCCC"));
        typingLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        inputField.setFont(Font.font("Consolas", FontWeight.NORMAL, 24));
        inputField.setStyle("-fx-text-fill: #CCCCCC; -fx-cursor: transparent;");

        startButton.setStyle("-fx-background-color: #333333; -fx-text-fill: #FFFFFF;");
        startButton.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        languageField.setFont(Font.font("Arial", FontWeight.NORMAL, 14));
        languageField.setStyle("-fx-text-fill: #CCCCCC; -fx-background-color: #2e2e2e;");


        languageField.setPromptText("Enter Language");
        startButton.setOnAction(event -> startTypingTest());
        inputField.setEditable(false);


        textFlow.setLayoutX(10);
        textFlow.setLayoutY(50);

        inputField.setMaxWidth(600);
        inputField.setLayoutX(10);
        inputField.setLayoutY(50);
        inputField.setOpacity(0.0);
        inputField.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");

        inputField.setOnKeyTyped(event -> handleTyping(event.getCharacter()));
    }

    private void jumpText(Text text) {
        TranslateTransition transition = new TranslateTransition(Duration.millis(150), text);
        transition.setByY(-20);
        transition.setAutoReverse(true);
        transition.setCycleCount(2);
        transition.play();
    }

    private void jumpWord() {
        int startIndex = getLastWordStartIndex();
        for (int i = startIndex; i < charIndex; i++) {
            Text textChar = (Text) textFlow.getChildren().get(i);
            jumpText(textChar);
        }
    }

    private void showStatisticsWindow(double averageWpm) {
        Stage statisticsStage = new Stage();
        statisticsStage.initModality(Modality.APPLICATION_MODAL);
        statisticsStage.setTitle("Statistics");

        VBox layout = new VBox(10);
        layout.setStyle("-fx-background-color: #333; -fx-padding: 20;");

        Label wpmLabel = new Label(String.format("Average WPM: %.2f", averageWpm));
        wpmLabel.setStyle("-fx-text-fill: #fff; -fx-font-size: 24;");

        layout.getChildren().addAll(wpmLabel);

        Scene scene = new Scene(layout, 300, 200);
        statisticsStage.setScene(scene);
        statisticsStage.showAndWait();
    }

    private void setupTextFlowWithTestText() {
        textFlow.getChildren().clear();
        for (char c : testText.toCharArray()) {
            Text textChar = new Text(String.valueOf(c));
            textChar.setFill(Color.GRAY);
            textChar.setFont(Font.font("Consolas", FontWeight.NORMAL, 36));
            textFlow.getChildren().add(textChar);
        }
    }
    /**
     CONTROLLER  *******************************************************************************************************
     */
    private void setupKeyCombinations(Scene scene) {
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER && event.isShiftDown()) {
                restartGame();
            } else if (event.getCode() == KeyCode.P && event.isControlDown() && event.isShiftDown()) {
                pauseGame();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                endGame();
            }
        });
    }

    private void restartGame() {
        startTypingTest();
    }

    private void pauseGame() {
        if (typingTask != null) {
            typingTask.cancel();
            if (typingThread != null) {
                typingThread.interrupt();
            }
        }
    }

    private void endGame() {
        Platform.exit();
    }

    private String getRandomTextPassage(String filePath, int numberOfLines) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(filePath), StandardCharsets.UTF_8);
        Random random = new Random();
        StringBuilder textBuilder = new StringBuilder();

        for (int i = 0; i < numberOfLines; i++) {
            textBuilder.append(lines.get(random.nextInt(lines.size()))).append(" ");
        }

        return textBuilder.toString().trim();
    }

    private void startTypingTest() {
        correctChars = 0;
        incorrectChars = 0;
        extraChars = 0;
        missedChars = 0;
        charIndex = 0;

        if (typingTask != null && typingThread != null) {
            typingTask.cancel();
            typingThread.interrupt();
        }

        String languageInput = languageField.getText().trim();
        if (languageInput.isEmpty()) {
            typingLabel.setText("Please enter a language.");
            return;
        }

        try {
            String filePath = "dictionary/" + languageInput + ".txt";
            testText = getRandomTextPassage(filePath, 30);
            setupTextFlowWithTestText();
        } catch (IOException e) {
            typingLabel.setText("Error: Could not load text for language " + languageInput);
            return;
        }

        inputField.setEditable(true);
        inputField.clear();
        inputField.requestFocus();
        isTestActive = true;

        int durationInSeconds = durationComboBox.getValue() * 1000;

        typingTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                startTime = System.currentTimeMillis();
                while (!isCancelled()) {
                    elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
                    Platform.runLater(() -> typingLabel.setText("Time: " + elapsedSeconds + " seconds"));
                    if (elapsedSeconds >= durationInSeconds / 1000) {
                        Platform.runLater(() -> displayStatistics());
                        break;
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        if (isCancelled()) {
                            break;
                        }
                    }
                }
                return null;
            }
        };

        typingThread = new Thread(typingTask);
        typingThread.setDaemon(true);
        typingThread.start();
    }

    private void handleTyping(String typedChar) {
        if (String.valueOf(testText.charAt(charIndex)).equals(typedChar)) {
            correctChars++;
        } else {
            incorrectChars++;
        }
        if (typedChar.isEmpty()) {
            return;
        }

        if (typedChar.equals(" ")) {
            if (isWordCorrect()) {
                jumpWord();
            }
            charIndex++;
            return;
        }

        if (typedChar.equals("\b") || typedChar.equals("\u0008")) {
            if (charIndex > 0) {
                charIndex--;
                Text textToReset = (Text) textFlow.getChildren().get(charIndex);
                textToReset.setFill(Color.GRAY);
            }
            return;
        }

        if (charIndex >= testText.length()) {
            return;
        }

        Text currentTextChar = (Text) textFlow.getChildren().get(charIndex);
        if (String.valueOf(testText.charAt(charIndex)).equals(typedChar)) {
            currentTextChar.setFill(Color.GREEN);
            jumpText(currentTextChar);
        } else {
            currentTextChar.setFill(Color.RED);
        }
        charIndex++;
    }

    private boolean isWordCorrect() {
        int startIndex = getLastWordStartIndex();
        for (int i = startIndex; i < charIndex; i++) {
            Text textChar = (Text) textFlow.getChildren().get(i);
            if (!Color.GREEN.equals(textChar.getFill())) {
                return false;
            }
        }
        return true;
    }

    private int getLastWordStartIndex() {
        int startIndex = charIndex;
        while (startIndex > 0 && !String.valueOf(testText.charAt(startIndex - 1)).equals(" ")) {
            startIndex--;
        }
        return startIndex;
    }

    private void displayStatistics() {
        if (typingTask != null) {
            typingTask.cancel();
        }

        elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;

        double averageWpm = calculateWPM();

        typingLabel.setText(String.format("Test ended. Time taken: %d seconds. Average WPM: %.2f", elapsedSeconds, averageWpm));

        writeResultsToFile(averageWpm);
        showStatisticsWindow(averageWpm);
    }

    private void writeResultsToFile(double averageWpm) {
        String fileName = "data.txt";
        Path filePath = Paths.get(fileName);

        try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(String.format("Average WPM: %.2f", averageWpm));
            writer.newLine();

            String[] words = testText.split("\\s+");
            for (String word : words) {
                int mockWpmForWord = new Random().nextInt(100) + 50;
                writer.write(word + " -> " + mockWpmForWord + "wpm");
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private double calculateWPM() {
        return (double) correctChars / 5 / (elapsedSeconds / 60.0);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
