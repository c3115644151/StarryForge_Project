package com.starryforge.features.ironheart.gui;

import org.bukkit.entity.Player;

public class GUIStateSession {
    private final Player player;
    private State currentState;
    private String activeBlueprintId;

    public GUIStateSession(Player player) {
        this.player = player;
        this.currentState = State.IDLE;
    }

    public enum State {
        IDLE, // Waiting for input
        FABRICATION, // Blueprint selected, filling components
        MODIFICATION // Weapon inserted, swapping components
    }

    public State getCurrentState() {
        return currentState;
    }

    public void transitionTo(State newState) {
        this.currentState = newState;
    }

    public String getActiveBlueprintId() {
        return activeBlueprintId;
    }

    public void setActiveBlueprintId(String activeBlueprintId) {
        this.activeBlueprintId = activeBlueprintId;
    }

    public Player getPlayer() {
        return player;
    }

    public void reset() {
        this.currentState = State.IDLE;
        this.activeBlueprintId = null;
    }
}
