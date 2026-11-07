package com.DatShop.service;

final class Text {

    private Text() {
    }

    static String trim(String value) {
        return value == null ? null : value.trim();
    }

    static String emptyToNull(String value) {
        String trimmed = trim(value);
        if (trimmed == null || trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }
}
