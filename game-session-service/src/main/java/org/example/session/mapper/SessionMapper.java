package org.example.session.mapper;

import org.example.session.domain.Session;
import org.example.session.storage.entity.SessionEntity;
import org.example.session.web.dto.SessionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface SessionMapper {

    @Mapping(target = "sessionId", source = "id")
    SessionResponse toResponse(Session session);

    @Mapping(target = "board", source = "board", qualifiedByName = "encodeBoard")
    @Mapping(target = "moves", ignore = true)
    SessionEntity toEntity(Session session);

    @Mapping(target = "board", source = "board", qualifiedByName = "encodeBoard")
    @Mapping(target = "moves", ignore = true)
    void updateEntity(Session session, @MappingTarget SessionEntity entity);

    default Session toDomain(SessionEntity entity, MoveMapper moveMapper) {
        Session session = new Session(entity.getId(), entity.getGameId());
        session.setBoard(decodeBoard(entity.getBoard()));
        session.setStatus(entity.getStatus());
        session.setWinner(entity.getWinner());
        entity.getMoves().stream()
                .map(moveMapper::toDomain)
                .forEach(session::addMove);
        return session;
    }

    @Named("encodeBoard")
    static String encodeBoard(String[][] board) {
        StringBuilder sb = new StringBuilder(9);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                String v = board[r][c];
                sb.append(v == null ? '_' : v.charAt(0));
            }
        }
        return sb.toString();
    }

    static String[][] decodeBoard(String board) {
        String[][] out = new String[3][3];
        for (int i = 0; i < 9; i++) {
            char ch = board.charAt(i);
            if (ch != '_') out[i / 3][i % 3] = String.valueOf(ch);
        }
        return out;
    }
}
