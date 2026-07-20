package com.fincore.api.util;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class ReferenceGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHANUM = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private ReferenceGenerator() {
    }

    /** e.g. FCR-20260702-K7X2M9QW4T */
    public static String generate() {
        StringBuilder suffix = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            suffix.append(ALPHANUM.charAt(RANDOM.nextInt(ALPHANUM.length())));
        }
        return "FCR-" + LocalDate.now().format(DATE) + "-" + suffix;
    }
}
