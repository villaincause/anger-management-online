package application;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

import java.io.File;
import java.util.Scanner;
import java.util.concurrent.Executors;

import application.net.HttpClientLite;

public class GameUI {
    private Stage primaryStage;
    private BorderPane root;
    private Player player1;
    private Player player2;
    private String player1Move = "";
    private String player2Move = "";
    private Label p1MessageLabel;
    private Label p2MessageLabel;

    private VBox startScreen;
    private Label gameTitle;
    private Label roundLabel;
    private Button startButton;
    private boolean gameStarted = false;

    private VBox p1MoveBox;
    private VBox p2MoveBox;
    private HBox actionButtonsBox;

    private MediaPlayer bgMusic;
    private AudioClip punchSound, kickSound, slapSound;

    private int angerScore;
    private int confidenceScore;
    private int satisfactionScore;
    private int additionalScore;

    // === Networking fields ===
    private HttpClientLite net;
    private String serverBase = "https://<your-render-service>.onrender.com"; // TODO: set your deployed server URL
    private String roomId = "room-123";
    private String myPlayer = "P1"; // or "P2"

    public GameUI() {
        // Action buttons container
        actionButtonsBox = new HBox(20);
        actionButtonsBox.setAlignment(Pos.CENTER);
        actionButtonsBox.getStyleClass().add("image-button-container");

        // Root layout and start screen
        root = new BorderPane();
        createStartScreen();
        root.setCenter(startScreen);

        // Player Declaration
        player1 = new Player("Player 1");
        player2 = new Player("Player 2");

        // Round Label
        roundLabel = new Label();
        roundLabel.getStyleClass().add("round-label");
        loadSounds();
        playBackgroundMusic();

        // Message labels
        p1MessageLabel = createMessageLabel();
        p2MessageLabel = createMessageLabel();

        // Move selection boxes
        p1MoveBox = createMoveSelectionBox("Player 1");
        p2MoveBox = createMoveSelectionBox("Player 2");
    }

    private void createStartScreen() {
        gameTitle = new Label("ANGER MANAGEMENT");
        gameTitle.getStyleClass().add("game-title");

        startButton = new Button("Start Game");
        startButton.setOnAction(e -> startGame());
        startButton.getStyleClass().add("control-button");

        startScreen = new VBox(20, gameTitle, startButton);
        startScreen.setAlignment(Pos.CENTER);
    }

    private void startGame() {
        gameStarted = true;

        VBox topUI = createTopUI();
        topUI.setPadding(new Insets(20, 0, 10, 0)); // optional
        topUI.getStyleClass().add("round-container");
        BorderPane.setAlignment(topUI, Pos.TOP_CENTER);
        root.setTop(topUI);

        // Player boxes
        VBox p1Box = player1.getUIBox();
        VBox p2Box = player2.getUIBox();
        p1Box.getStyleClass().addAll("player-ui", "player1-box");
        p2Box.getStyleClass().addAll("player-ui", "player2-box");
        p1Box.setPadding(new Insets(50));
        p2Box.setPadding(new Insets(50));
        root.setLeft(p1Box);
        root.setRight(p2Box);

        HBox centerBox = new HBox(50, p1MoveBox, p2MoveBox);
        centerBox.setAlignment(Pos.CENTER); // important!
        root.setCenter(centerBox);

        // Bottom Action buttons
        actionButtonsBox.getStyleClass().add("image-button-container");
        root.setBottom(actionButtonsBox);

        // Give Up buttons
        Button p1GiveUp = new Button("Give Up");
        p1GiveUp.setOnAction(e -> handleGiveUp(player1));
        p1GiveUp.getStyleClass().add("control-button");

        Button p2GiveUp = new Button("Give Up");
        p2GiveUp.setOnAction(e -> handleGiveUp(player2));
        p2GiveUp.getStyleClass().add("control-button");

        player1.getUIBox().getChildren().add(p1GiveUp);
        player2.getUIBox().getChildren().add(p2GiveUp);

        roundLabel.setText("Round: 1");
        setupKeyboardShortcuts();
        root.requestFocus();

        // Connect to server (online mode)
        connectOnline();
    }

    private VBox createTopUI() {
        HBox roundBox = new HBox(roundLabel);
        roundBox.setAlignment(Pos.CENTER);

        HBox messageBox = new HBox(200, p1MessageLabel, p2MessageLabel);
        messageBox.setAlignment(Pos.CENTER);

        Label controlsHint = new Label("Controls: P1 → Q / W / E    |    P2 → I / O / P");
        controlsHint.getStyleClass().add("controls-label");
        controlsHint.setAlignment(Pos.CENTER);

        VBox topBox = new VBox(10, gameTitle, roundBox, messageBox, controlsHint);
        topBox.setAlignment(Pos.CENTER);
        return topBox;
    }

    private void handleGiveUp(Player quitter) {
        showMessage(p1MessageLabel, quitter.getName() + " gave up!");
        disableAllButtons();
        if (quitter == player1) {
            showEndGamePopup("Player 2");
        } else {
            showEndGamePopup("Player 1");
        }
    }

    private void loadSounds() {
        punchSound = new AudioClip(new File("src/application/sounds/Punch.mp3").toURI().toString());
        kickSound = new AudioClip(new File("src/application/sounds/Kick.mp3").toURI().toString());
        slapSound = new AudioClip(new File("src/application/sounds/Slap.mp3").toURI().toString());
    }

    private void playBackgroundMusic() {
        Media bg = new Media(new File("src/application/sounds/BackgroundMusic.MP3").toURI().toString());
        bgMusic = new MediaPlayer(bg);
        bgMusic.setCycleCount(MediaPlayer.INDEFINITE);
        bgMusic.play();
    }

    private Label createMessageLabel() {
        Label label = new Label();
        label.setOpacity(0);
        label.getStyleClass().add("action-message");
        return label;
    }

    private VBox createMoveSelectionBox(String player) {
        VBox box = new VBox(10);
        box.setAlignment(Pos.CENTER);

        ImageView rock = createImageButton("rock.jpg", () -> selectMove(player, "rock"));
        ImageView paper = createImageButton("paper.jpg", () -> selectMove(player, "paper"));
        ImageView scissors = createImageButton("scissors.jpg", () -> selectMove(player, "scissors"));

        box.getChildren().addAll(rock, paper, scissors);
        return box;
    }

    private ImageView createImageButton(String imageName, Runnable action) {
        ImageView imageView = new ImageView(new Image("file:src/application/images/" + imageName));
        imageView.setFitHeight(80);
        imageView.setFitWidth(80);
        imageView.setOnMouseClicked(e -> action.run());
        imageView.getStyleClass().add("image-button");
        return imageView;
    }

    // === Online move submission ===
    private void selectMove(String player, String move) {
        // Only allow this client to send moves for its assigned player
        if ((player.equals("Player 1") && !"P1".equals(myPlayer)) ||
            (player.equals("Player 2") && !"P2".equals(myPlayer))) {
            return;
        }

        // Send to server; UI updates will arrive via events
        try {
            String body = "{\"room\":\"" + roomId + "\",\"player\":\"" + myPlayer + "\",\"move\":\"" + move + "\"}";
            net.post("/move", "", body);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupKeyboardShortcuts() {
        root.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                // Player 1
                case Q:
                    if (player1Move.isEmpty()) selectMove("Player 1", "rock");
                    break;
                case W:
                    if (player1Move.isEmpty()) selectMove("Player 1", "paper");
                    break;
                case E:
                    if (player1Move.isEmpty()) selectMove("Player 1", "scissors");
                    break;

                // Player 2
                case I:
                    if (player2Move.isEmpty()) selectMove("Player 2", "rock");
                    break;
                case O:
                    if (player2Move.isEmpty()) selectMove("Player 2", "paper");
                    break;
                case P:
                    if (player2Move.isEmpty()) selectMove("Player 2", "scissors");
                    break;
                default:
                    break;
            }
        });
    }

    private String capitalize(String word) {
        if (word == null || word.isEmpty()) return "";
        return word.substring(0, 1).toUpperCase() + word.substring(1);
    }

    private void showActionButtons(Player winner, Player loser, Label messageLabel) {
        actionButtonsBox.getChildren().clear();

        actionButtonsBox.getChildren().addAll(
                createActionButton("kick.jpg", "Kick", winner, loser, messageLabel),
                createActionButton("punch.jpg", "Punch", winner, loser, messageLabel),
                createActionButton("slap.jpg", "Slap", winner, loser, messageLabel)
        );
    }

    private ImageView createActionButton(String img, String action, Player winner, Player loser, Label label) {
        ImageView imgView = new ImageView(new Image("file:src/application/images/" + img));
        imgView.setFitWidth(80);
        imgView.setFitHeight(80);
        imgView.setOnMouseClicked(e -> {
            // Send action to server; server determines points and broadcasts new state.
            sendAction(action);
        });
        imgView.getStyleClass().add("image-button");
        return imgView;
    }

    private void playSound(String action) {
        switch (action) {
            case "Punch": punchSound.play(); break;
            case "Kick":  kickSound.play();  break;
            case "Slap":  slapSound.play();  break;
        }
    }

    private void showMessage(Label label, String text) {
        label.setText(text);
        FadeTransition ft = new FadeTransition(Duration.seconds(1.5), label);
        ft.setFromValue(1.0);
        ft.setToValue(0.0);
        ft.play();
    }

    private void resetMoves() {
        player1Move = "";
        player2Move = "";
    }

    private void disableAllButtons() {
        for (javafx.scene.Node node : p1MoveBox.getChildren()) node.setDisable(true);
        for (javafx.scene.Node node : p2MoveBox.getChildren()) node.setDisable(true);
        for (javafx.scene.Node node : actionButtonsBox.getChildren()) node.setDisable(true);
    }

    private void checkWinCondition() {
        if (player1.getScore() >= 50 || player2.getScore() >= 50) {
            String winnerName = player1.getScore() >= 50 ? player1.getName() : player2.getName();
            showMessage(p1MessageLabel, winnerName + " wins the game!");
            showMessage(p2MessageLabel, winnerName + " wins the game!");
            disableAllButtons();
            showEndGamePopup(winnerName);
        }
    }

    private void showEndGamePopup(String winnerName) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Game Over");
            alert.setHeaderText("🏆 " + winnerName + " Wins!");
            alert.setContentText("Congratulations, " + winnerName + "! Thanks for playing Anger Management.");

            ButtonType closeButton = new ButtonType("Close Game", ButtonBar.ButtonData.OK_DONE);
            alert.getButtonTypes().setAll(closeButton);

            if (primaryStage != null) {
                alert.initOwner(primaryStage);
            }

            alert.showAndWait().ifPresent(response -> {
                if (response == closeButton) {
                    Platform.exit();
                    System.exit(0);
                }
            });
        });
    }

    public Pane getRoot(Stage primaryStage) {
        this.primaryStage = primaryStage;
        return root;
    }

    // === Networking helpers ===

    private void connectOnline() {
        net = new HttpClientLite(serverBase);
        // Join the room
        try {
            String body = "room=" + roomId + "&name=" + (myPlayer.equals("P1") ? "Player 1" : "Player 2");
            net.post("/join", body, "");
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Listen for server-sent events
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                Scanner sc = net.getEvents("/events?room=" + roomId);
                while (sc.hasNextLine()) {
                    String line = sc.nextLine();
                    if (line.startsWith("data:")) {
                        String json = line.substring(5).trim();
                        Platform.runLater(() -> handleServerState(json));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void sendAction(String action) {
        try {
            String body = "{\"room\":\"" + roomId + "\",\"player\":\"" + myPlayer + "\",\"action\":\"" + action + "\"}";
            net.post("/action", "", body);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleServerState(String json) {
        // Minimal parser aligned with AngerServer.toJsonState and extras
        // Updates: round, player bars/scores, moves, result, pendingActionFor, lastAction, actor, winnerName, gameOver
        int round = parseInt(json, "\"round\":", 1);
        roundLabel.setText("Round: " + round);

        // p1 state
        int p1Score = parseInt(json, "\"p1\":{\"name\":", "\"score\":", 0);
        int p1Anger = parseInt(json, "\"p1\"", "\"anger\":", 50);
        int p1Satisfaction = parseInt(json, "\"p1\"", "\"satisfaction\":", 25);
        int p1Confidence = parseInt(json, "\"p1\"", "\"confidence\":", 0);
        player1.setScore(p1Score);
        player1.setAnger(p1Anger);
        player1.setSatisfaction(p1Satisfaction);
        player1.setConfidence(p1Confidence);

        // p2 state
        int p2Score = parseInt(json, "\"p2\":{\"name\":", "\"score\":", 0);
        int p2Anger = parseInt(json, "\"p2\"", "\"anger\":", 50);
        int p2Satisfaction = parseInt(json, "\"p2\"", "\"satisfaction\":", 25);
        int p2Confidence = parseInt(json, "\"p2\"", "\"confidence\":", 0);
        player2.setScore(p2Score);
        player2.setAnger(p2Anger);
        player2.setSatisfaction(p2Satisfaction);
        player2.setConfidence(p2Confidence);

        // moves and result
        player1Move = parseString(json, "\"p1Move\":\"", "");
        player2Move = parseString(json, "\"p2Move\":\"", "");
        String result = parseString(json, "\"result\":\"", "");
        String pendingActionFor = parseString(json, "\"pendingActionFor\":\"", "");

        // messages
        if (!player1Move.isEmpty()) showMessage(p1MessageLabel, "Player 1 chose " + capitalize(player1Move));
        if (!player2Move.isEmpty()) showMessage(p2MessageLabel, "Player 2 chose " + capitalize(player2Move));

        if ("DRAW".equals(result)) {
            showMessage(p1MessageLabel, "Draw!");
            showMessage(p2MessageLabel, "Draw!");
            actionButtonsBox.getChildren().clear();
            resetMoves();
        } else if ("P1_WIN".equals(result)) {
            showMessage(p1MessageLabel, "Player 1 won this round!");
        } else if ("P2_WIN".equals(result)) {
            showMessage(p2MessageLabel, "Player 2 won this round!");
        }

        // Show action buttons only for the winner client
        actionButtonsBox.getChildren().clear();
        if ("P1".equals(pendingActionFor)) {
            if ("P1".equals(myPlayer)) showActionButtons(player1, player2, p1MessageLabel);
        } else if ("P2".equals(pendingActionFor)) {
            if ("P2".equals(myPlayer)) showActionButtons(player2, player1, p2MessageLabel);
        }

        // lastAction feedback, game over
        String lastAction = parseString(json, "\"lastAction\":\"", "");
        String actor = parseString(json, "\"actor\":\"", "");
        String winnerName = parseString(json, "\"winnerName\":\"", "");
        boolean gameOver = parseBool(json, "\"gameOver\":", false);

        if (!lastAction.isEmpty() && !actor.isEmpty()) {
            // Play corresponding sound and message
            playSound(lastAction);
            if ("P1".equals(actor)) {
                showMessage(p1MessageLabel, "Player 1 " + lastAction.toLowerCase() + "ed Player 2!");
            } else if ("P2".equals(actor)) {
                showMessage(p2MessageLabel, "Player 2 " + lastAction.toLowerCase() + "ed Player 1!");
            }
            // After an action, server resets moves and advances round
            resetMoves();
            actionButtonsBox.getChildren().clear();
        }

        if (gameOver && !winnerName.isEmpty()) {
            showMessage(p1MessageLabel, winnerName + " wins the game!");
            showMessage(p2MessageLabel, winnerName + " wins the game!");
            disableAllButtons();
            showEndGamePopup(winnerName);
        }
    }

    // === Tiny parsing helpers (no external JSON lib) ===

    private String parseString(String json, String key, String def) {
        int i = json.indexOf(key);
        if (i < 0) return def;
        int s = i + key.length();
        int e = json.indexOf("\"", s);
        if (e < 0) return def;
        return json.substring(s, e);
    }

    private int parseInt(String json, String sectionKey, String key, int def) {
        // Optional: narrow to section if present
        int base = json.indexOf(sectionKey);
        int startSearch = base >= 0 ? base : 0;
        int i = json.indexOf(key, startSearch);
        if (i < 0) return def;
        int s = i + key.length();
        // read until non-digit (allow minus)
        int e = s;
        while (e < json.length()) {
            char c = json.charAt(e);
            if ((c >= '0' && c <= '9') || c == '-') e++;
            else break;
        }
        try { return Integer.parseInt(json.substring(s, e)); } catch (Exception ex) { return def; }
    }

    private int parseInt(String json, String key, int def) {
        int i = json.indexOf(key);
        if (i < 0) return def;
        int s = i + key.length();
        int e = s;
        while (e < json.length()) {
            char c = json.charAt(e);
            if ((c >= '0' && c <= '9') || c == '-') e++;
            else break;
        }
        try { return Integer.parseInt(json.substring(s, e)); } catch (Exception ex) { return def; }
    }

    private boolean parseBool(String json, String key, boolean def) {
        int i = json.indexOf(key);
        if (i < 0) return def;
        int s = i + key.length();
        // expect true/false
        if (json.startsWith("true", s)) return true;
        if (json.startsWith("false", s)) return false;
        return def;
    }
}