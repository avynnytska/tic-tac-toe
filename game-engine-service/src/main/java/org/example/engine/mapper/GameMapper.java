package org.example.engine.mapper;

import org.example.engine.domain.Game;
import org.example.engine.domain.Player;
import org.example.engine.storage.entity.GameEntity;
import org.example.engine.web.dto.GameResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface GameMapper {

    @Mapping(target = "board", source = "board", qualifiedByName = "toResponseBoard")
    GameResponse toResponse(Game game);

    @Mapping(target = "board", source = "board", qualifiedByName = "encodeBoard")
    GameEntity toEntity(Game game);

    default Game toDomain(GameEntity entity) {
        return Game.restore(
                entity.getId(),
                decodeBoard(entity.getBoard()),
                entity.getNextPlayer(),
                entity.getStatus(),
                entity.getWinner()
        );
    }

    @Named("toResponseBoard")
    static String[][] toResponseBoard(Player[][] board) {
        String[][] out = new String[3][3];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                out[r][c] = board[r][c] == null ? null : board[r][c].name();
            }
        }
        return out;
    }

    @Named("encodeBoard")
    static String encodeBoard(Player[][] board) {
        StringBuilder sb = new StringBuilder(9);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                sb.append(board[r][c] == null ? '_' : board[r][c].name());
            }
        }
        return sb.toString();
    }

    static Player[][] decodeBoard(String board) {
        Player[][] out = new Player[3][3];
        for (int i = 0; i < 9; i++) {
            char ch = board.charAt(i);
            if (ch != '_') out[i / 3][i % 3] = Player.valueOf(String.valueOf(ch));
        }
        return out;
    }
}
