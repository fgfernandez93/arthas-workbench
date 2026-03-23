package com.alibaba.arthas.idea.workbench.util;

import com.alibaba.arthas.idea.workbench.model.ArthasSession;

/**
 * 负责把单会话或 Gateway 会话格式化为可直接复制使用的 MCP 配置片段。
 */
public final class McpConfigFormatter {

    private static final String GATEWAY_SERVER_NAME = "idea-arthas-workbench";
    private static final int MAX_SERVER_NAME_BODY_LENGTH = 36;

    private McpConfigFormatter() {}

    /**
     * 生成直连某个 Arthas 会话的 MCP 配置。
     */
    public static String format(ArthasSession session) {
        return format(buildServerName("arthas", session), session.getMcpUrl(), session.getMcpPassword());
    }

    /**
     * 生成经由插件 Gateway 访问统一 MCP 服务的配置。
     */
    public static String formatGateway(ArthasSession session, String gatewayUrl) {
        return formatGateway(gatewayUrl, "");
    }

    public static String formatGateway(ArthasSession session, String gatewayUrl, String gatewayToken) {
        return formatGateway(gatewayUrl, gatewayToken);
    }

    /**
     * 生成经由插件 Gateway 访问统一 MCP 服务的配置。
     */
    public static String formatGateway(String gatewayUrl) {
        return formatGateway(gatewayUrl, "");
    }

    public static String formatGateway(String gatewayUrl, String gatewayToken) {
        return format(GATEWAY_SERVER_NAME, gatewayUrl, gatewayToken == null ? "" : gatewayToken.trim());
    }

    static String buildServerName(String prefix, ArthasSession session) {
        String namePart = normalizeServerNamePart(session.getDisplayName());
        if (namePart.isBlank()) {
            return prefix + "-" + session.getPid();
        }
        return prefix + "-" + namePart + "-" + session.getPid();
    }

    static String normalizeServerNamePart(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().toLowerCase();
        normalized = normalized.replaceAll("[^a-z0-9]+", "-");
        normalized = normalized.replaceAll("-{2,}", "-");
        normalized = normalized.replaceAll("^-+|-+$", "");
        if (normalized.length() > MAX_SERVER_NAME_BODY_LENGTH) {
            normalized = normalized.substring(0, MAX_SERVER_NAME_BODY_LENGTH);
            normalized = normalized.replaceAll("-+$", "");
        }
        return normalized;
    }

    private static String format(String serverName, String url, String bearerToken) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  \"mcpServers\": {\n");
        builder.append("    \"").append(serverName).append("\": {\n");
        builder.append("      \"type\": \"streamableHttp\",\n");
        builder.append("      \"url\": \"").append(url).append("\"");
        if (!bearerToken.isBlank()) {
            builder.append(",\n");
            builder.append("      \"headers\": {\n");
            builder.append("        \"Authorization\": \"Bearer ")
                    .append(bearerToken)
                    .append("\"\n");
            builder.append("      }\n");
        } else {
            builder.append("\n");
        }
        builder.append("    }\n");
        builder.append("  }\n");
        builder.append("}\n");
        return builder.toString();
    }
}
