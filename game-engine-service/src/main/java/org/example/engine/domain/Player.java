package org.example.engine.domain;

public enum Player {
    X, O;

    public Player opponent() {
        return this == X ? O : X;
    }
}
