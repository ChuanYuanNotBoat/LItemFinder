package dev.litemfinder.neoforge.identity;

import java.net.IDN;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/** Creates stable privacy-preserving scope identifiers for saves and servers. */
public final class ScopeIdFactory {

    private static final String PREFIX = "scope:v1:";

    private ScopeIdFactory() {
    }

    public static String singleplayer(String worldIdentity) {
        String normalized = normalizeText(worldIdentity, "worldIdentity").replace('\\', '/');
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("worldIdentity must not be blank after normalization");
        }
        return digest("singleplayer\0" + normalized);
    }

    public static String multiplayer(String host, int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        String normalizedHost = normalizeText(host, "host");
        if (normalizedHost.startsWith("[") && normalizedHost.endsWith("]")) {
            normalizedHost = normalizedHost.substring(1, normalizedHost.length() - 1);
        }
        if (!normalizedHost.contains(":")) {
            while (normalizedHost.endsWith(".")) {
                normalizedHost = normalizedHost.substring(0, normalizedHost.length() - 1);
            }
            normalizedHost = IDN.toASCII(normalizedHost);
        }
        normalizedHost = normalizedHost.toLowerCase(Locale.ROOT);
        if (normalizedHost.isBlank()) {
            throw new IllegalArgumentException("host must not be blank after normalization");
        }
        return digest("multiplayer\0" + normalizedHost + ":" + port);
    }

    private static String normalizeText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static String digest(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return PREFIX + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
