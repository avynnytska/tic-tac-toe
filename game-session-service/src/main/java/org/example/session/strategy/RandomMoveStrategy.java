package org.example.session.strategy;

import org.example.session.domain.Player;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Component
public class RandomMoveStrategy implements MoveStrategy {

    private final Random random = new Random();

    @Override
    public int[] nextMove(String[][] board, Player player) {
        List<int[]> empty = new ArrayList<>();
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (board[r][c] == null) empty.add(new int[]{r, c});
            }
        }
        if (empty.isEmpty()) {
            throw new IllegalStateException("No empty cells available");
        }
        Collections.shuffle(empty, random);
        return empty.get(0);
    }
}
