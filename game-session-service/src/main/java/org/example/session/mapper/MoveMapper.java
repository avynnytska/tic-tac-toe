package org.example.session.mapper;

import org.example.session.domain.Move;
import org.example.session.storage.entity.MoveEntity;
import org.example.session.storage.entity.SessionEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MoveMapper {

    default Move toDomain(MoveEntity entity) {
        return new Move(entity.getPlayer(), entity.getRow(), entity.getCol(), entity.getPlayedAt());
    }

    default MoveEntity toEntity(Move move, SessionEntity session) {
        return new MoveEntity(session, move.player(), move.row(), move.col(), move.at());
    }
}
