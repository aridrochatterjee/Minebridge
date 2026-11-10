package com.minebridge.death;

public record DeathContext(
        String victim,
        String killer,
        String cause,
        DeathCategory category) {

    public DeathContext {
        victim = victim == null ? "" : victim;
        killer = killer == null ? "" : killer;
        cause = cause == null ? "an unknown cause" : cause;
    }
}
