package com.minipaintdex.application;

import java.security.SecureRandom;
import java.time.Instant;

final class Ulid {
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ulid() {}

    static String next(Instant instant) {
        var time = instant.toEpochMilli();
        var result = new char[26];
        for (var index = 9; index >= 0; index--) {
            result[index] = ALPHABET[(int) (time & 31)];
            time >>>= 5;
        }
        var bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        for (var index = 10; index < result.length; index++) result[index] = ALPHABET[Byte.toUnsignedInt(bytes[index - 10]) & 31];
        return new String(result);
    }
}
