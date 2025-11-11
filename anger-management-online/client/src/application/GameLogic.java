package application;

public class GameLogic {
	
	private static int round = 1;

	public static int getRound() {
	    return round;
	}

	public static void nextRound() {
	    round++;
	}

    public enum Result {
        P1_WIN,
        P2_WIN,
        DRAW
    }

    public static Result determineWinner(String p1Move, String p2Move) {
        if (p1Move.equals(p2Move)) {
            return Result.DRAW;
        } else if ((p1Move.equals("rock") && p2Move.equals("scissors")) ||
                   (p1Move.equals("paper") && p2Move.equals("rock")) ||
                   (p1Move.equals("scissors") && p2Move.equals("paper"))) {
            return Result.P1_WIN;
        } else {
            return Result.P2_WIN;
        }
    }
}