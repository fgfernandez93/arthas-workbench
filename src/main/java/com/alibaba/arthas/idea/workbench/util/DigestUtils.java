package com.alibaba.arthas.idea.workbench.util;

import com.alibaba.arthas.idea.workbench.ArthasWorkbenchBundle;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 提供插件内部需要的轻量摘要能力。
 */
public final class DigestUtils {

    private DigestUtils() {}

    public static String sha256(String input) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    ArthasWorkbenchBundle.message("util.digest.error.sha256_unsupported"), exception);
        }
    }
}
