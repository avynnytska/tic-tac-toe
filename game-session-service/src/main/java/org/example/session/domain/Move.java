package org.example.session.domain;

import java.time.Instant;

public record Move(Player player, int row, int col, Instant at) {}
