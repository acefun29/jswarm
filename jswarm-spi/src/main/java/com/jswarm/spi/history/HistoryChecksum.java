// Canonical 历史 checksum
package com.jswarm.spi.history;

import com.jswarm.spi.message.CanonicalMessage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public final class HistoryChecksum {

    private HistoryChecksum() {
    }

    public static String sha256(List<CanonicalMessage> messages) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (CanonicalMessage message : messages != null ? messages : List.<CanonicalMessage>of()) {
                digest.update(message.role().name().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(message.text().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                for (var call : message.toolCalls()) {
                    digest.update(call.id().getBytes(StandardCharsets.UTF_8));
                    digest.update(call.name().getBytes(StandardCharsets.UTF_8));
                    digest.update(call.arguments().getBytes(StandardCharsets.UTF_8));
                }
                if (message.toolCallId() != null) {
                    digest.update(message.toolCallId().getBytes(StandardCharsets.UTF_8));
                }
                if (message.toolName() != null) {
                    digest.update(message.toolName().getBytes(StandardCharsets.UTF_8));
                }
                digest.update((byte) 1);
            }
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest.digest()) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
