package org.example.session.strategy;

import org.example.session.domain.Player;

public interface MoveStrategy {
    int[] nextMove(String[][] board, Player player);
}
