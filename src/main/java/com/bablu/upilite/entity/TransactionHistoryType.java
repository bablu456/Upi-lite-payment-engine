package com.bablu.upilite.entity;

public enum TransactionHistoryType {
    ALL,
    CREDIT,
    DEBIT;

    public static TransactionHistoryType from(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return ALL;
        }

        for (TransactionHistoryType candidate : values()) {
            if (candidate.name().equalsIgnoreCase(rawValue.trim())) {
                return candidate;
            }
        }

        throw new IllegalArgumentException("Unsupported transaction history type: " + rawValue);
    }
}
