package com.fincore.api.util;

import java.security.SecureRandom;

public final class AccountNumberGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private AccountNumberGenerator() {
    }

    /** Generates a NUBAN-style 10-digit account number. */
    public static String generate() {
        StringBuilder sb = new StringBuilder(10);
        sb.append(RANDOM.nextInt(9) + 1); // no leading zero
        for (int i = 1; i < 10; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
