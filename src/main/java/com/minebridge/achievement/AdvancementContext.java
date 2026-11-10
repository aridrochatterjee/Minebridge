package com.minebridge.achievement;

public record AdvancementContext(
        String player,
        String title,
        String description,
        AdvancementCategory category,
        boolean challenge) {

    public AdvancementContext {
        player = player == null ? "" : player;
        title = title == null ? "Unknown advancement" : title;
        description = description == null ? "" : description;
    }

    public String typeLabel() {
        return challenge ? "Challenge" : "Advancement";
    }
}
