package com.ayush.gcnclient.alert;

public enum AlertType {
    FIFTEEN_MINUTES_BEFORE,
    FIVE_MINUTES_BEFORE,
    STARTING_NOW;

    public String message() {
        return switch (this) {
            case FIFTEEN_MINUTES_BEFORE -> "15 minutes";
            case FIVE_MINUTES_BEFORE -> "5 minutes";
            case STARTING_NOW -> "Starting now";
            default -> super.toString();
        };
    }
}
