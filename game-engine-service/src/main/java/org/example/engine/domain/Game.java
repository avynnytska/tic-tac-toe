package org.example.engine.domain;

import lombok.Getter;

import java.util.UUID;

@Getter
public class Game {
    private final UUID id;
    private final Player[][] board = new Player[3][3];
    private Player nextPlayer = Player.X;
    private GameStatus status = GameStatus.IN_PROGRESS;
    private Player winner;

    public Game(UUID id) {
        this.id = id;
    }

    public static Game restore(UUID id, Player[][] board, Player nextPlayer, GameStatus status, Player winner) {
        Game g = new Game(id);
        for (int r = 0; r < 3; r++) System.arraycopy(board[r], 0, g.board[r], 0, 3);
        g.nextPlayer = nextPlayer;
        g.status = status;
        g.winner = winner;
        return g;
    }

    public synchronized void applyMove(Player player, int row, int col) {
        board[row][col] = player;
        nextPlayer = player.opponent();
        recomputeStatus();
    }

    private void recomputeStatus() {
        Player win = findWinner();
        if (win != null) {
            winner = win;
            status = (win == Player.X) ? GameStatus.X_WON : GameStatus.O_WON;
            return;
        }
        if (isBoardFull()) {
            status = GameStatus.DRAW;
        }
    }

    private Player findWinner() {
        for (int i = 0; i < 3; i++) {
            if (board[i][0] != null && board[i][0] == board[i][1] && board[i][1] == board[i][2]) {
                return board[i][0];
            }
            if (board[0][i] != null && board[0][i] == board[1][i] && board[1][i] == board[2][i]) {
                return board[0][i];
            }
        }
        if (board[0][0] != null && board[0][0] == board[1][1] && board[1][1] == board[2][2]) {
            return board[0][0];
        }
        if (board[0][2] != null && board[0][2] == board[1][1] && board[1][1] == board[2][0]) {
            return board[0][2];
        }
        return null;
    }

    private boolean isBoardFull() {
        for (Player[] row : board) {
            for (Player cell : row) {
                if (cell == null) return false;
            }
        }
        return true;
    }

    public boolean isCellEmpty(int row, int col) {
        return board[row][col] == null;
    }
}
