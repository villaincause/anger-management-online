package application;

import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

public class Player {
    private String name;
    private int score; 
    
    private int anger = 50;
    private int satisfaction = 25;
    private int confidence = 0;
    
    private ProgressBar angerBar;
    private ProgressBar satisfactionBar;
    private ProgressBar confidenceBar;
    
    private Label nameLabel;
    private Label scoreLabel;
    Label angerLabel = createStateLabel("Anger");
    Label satisfactionLabel = createStateLabel("Satisfaction");
    Label confidenceLabel = createStateLabel("Confidence");
    private VBox uiBox;

    public static final String P1_WIN = "P1_WIN";
    public static final String P2_WIN = "P2_WIN";

    public Player(String name) {
        this.name = name;
        this.score = 0;

        nameLabel = new Label(name);
        nameLabel.setFont(Font.font("Arial", 18));
        nameLabel.getStyleClass().add("name-label");

        scoreLabel = new Label("Score: 0");
        scoreLabel.getStyleClass().add("score-label");

        angerBar = createStateBar();
        satisfactionBar = createStateBar();
        confidenceBar = createStateBar();

        uiBox = new VBox(5,
            nameLabel,
            scoreLabel,
            angerLabel, angerBar,
            satisfactionLabel, satisfactionBar,
            confidenceLabel, confidenceBar
        );

        uiBox.getStyleClass().add("player-ui");
    }
    
    private Label createStateLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("state-label");
        return label;
    }


    public String getName() {
        return name;
    }

    public int getScore() {
        return score;
    }

    public Label getScoreLabel() {
        return scoreLabel;
    }
    
    public int getConfidence() {
        return confidence;
    }
    
    public int getAnger() {
        return anger;
    }
    
    public int getSatisfaction() {
        return satisfaction;
    }

    public VBox getUIBox() {
        return uiBox;
    }

    public void setUIBars() {
        scoreLabel.setText("Score: " + score);
    }
    
    private ProgressBar createStateBar() {
        ProgressBar bar = new ProgressBar();
        bar.setPrefWidth(150);
        bar.getStyleClass().add("mind-bar");
        return bar;
    }

    public void changeStateOfMind(String type, int delta) {
        switch (type) {
            case "anger":
                anger = clamp(anger + delta);
                break;
            case "satisfaction":
                satisfaction = clamp(satisfaction + delta);
                break;
            case "confidence":
                confidence = clamp(confidence + delta);
                break;
        }
        updateUI();
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
    
    public void addScore(int points) {
        this.score += points;
        updateUI();
    }

    private void updateUI() {
        scoreLabel.setText("Score: " + score);
        angerBar.setProgress(anger / 100.0);
        satisfactionBar.setProgress(satisfaction / 100.0);
        confidenceBar.setProgress(confidence / 100.0);
    }
    
 // Add these methods at the bottom of Player.java
    public void setScore(int s){ this.score = s; updateUI(); }
    public void setAnger(int a){ this.anger = clamp(a); updateUI(); }
    public void setSatisfaction(int s){ this.satisfaction = clamp(s); updateUI(); }
    public void setConfidence(int c){ this.confidence = clamp(c); updateUI(); }
    
}