package com.alibaba.arthas.idea.workbench.service;

import com.alibaba.arthas.idea.workbench.ArthasWorkbenchBundle;
import com.alibaba.arthas.idea.workbench.model.ArthasSession;
import com.alibaba.arthas.idea.workbench.model.PortAllocationMode;
import com.alibaba.arthas.idea.workbench.model.SessionStatus;
import com.alibaba.arthas.idea.workbench.util.PortToolkit;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 提供一个本地 MCP Gateway，将多个 Arthas 会话聚合到统一入口并代理转发请求。
 */
@Service(Service.Level.APP)
public final class ArthasMcpGatewayService implements Disposable {

    private static final String HOST = "127.0.0.1";
    private static final int DEFAULT_GATEWAY_PORT = 18765;
    private static final String BASE_PATH = "/gateway";
    private static final String CONTENT_TYPE_JSON = "application/json; charset=UTF-8";
    private static final String CONTENT_TYPE_ACCEPT = "application/json, text/event-stream";
    private static final String MCP_SESSION_HEADER = "Mcp-Session-Id";
    private static final String UNIFIED_MCP_PATH = "/mcp";
    private static final String UNIFIED_MCP_SERVER_NAME = "idea-arthas-workbench";
    private static final String UNIFIED_MCP_SESSION_ID = "idea-arthas-workbench-gateway";
    private static final String GATEWAY_SESSIONS_TOOL = "gateway_sessions";
    private static final String ROUTE_ARGUMENT_PID = "pid";
    private static final String ROUTE_ARGUMENT_SESSION_ID = "sessionId";
    private static final String DEFAULT_PROTOCOL_VERSION = "2025-03-26";
    private static final Gson GSON = new Gson();

    private final SessionRegistry sessionRegistry;
    private final IntSupplier preferredPortSupplier;
    private final Supplier<String> tokenSupplier;
    private final Object lifecycleLock = new Object();

    private HttpServer server;
    private ExecutorService executor;
    private int boundPort = -1;
    private int preferredPort = Integer.MIN_VALUE;

    public ArthasMcpGatewayService() {
        this(
                new LiveSessionRegistry(),
                () -> {
                    ArthasWorkbenchSettingsService settingsService =
                            ApplicationManager.getApplication().getService(ArthasWorkbenchSettingsService.class);
                    return parseGatewayPort(settingsService == null ? null : settingsService.getState().mcpGatewayPort);
                },
                () -> {
                    ArthasWorkbenchSettingsService settingsService =
                            ApplicationManager.getApplication().getService(ArthasWorkbenchSettingsService.class);
                    return settingsService == null ? "" : settingsService.resolveGatewayToken();
                });
    }

    ArthasMcpGatewayService(SessionRegistry sessionRegistry, IntSupplier preferredPortSupplier) {
        this(sessionRegistry, preferredPortSupplier, () -> "");
    }

    ArthasMcpGatewayService(
            SessionRegistry sessionRegistry, IntSupplier preferredPortSupplier, Supplier<String> tokenSupplier) {
        this.sessionRegistry = sessionRegistry;
        this.preferredPortSupplier = preferredPortSupplier;
        this.tokenSupplier = tokenSupplier;
    }

    /**
     * 返回 Gateway 根地址，必要时会先懒加载启动内置 HTTP 服务。
     */
    public String getBaseUrl() {
        ensureStarted();
        return "http://" + HOST + ":" + boundPort + BASE_PATH;
    }

    public String getSessionsUrl() {
        return getBaseUrl() + "/sessions";
    }

    /**
     * 返回新的统一 MCP 入口；客户端可通过 `pid` 或 `sessionId` 参数路由到目标会话。
     */
    public String getUnifiedGatewayMcpUrl() {
        return getBaseUrl() + UNIFIED_MCP_PATH;
    }

    /**
     * 返回兼容旧行为的按 PID 定位入口，便于调试和回归验证。
     */
    public String getGatewayMcpUrl(ArthasSession session) {
        return getBaseUrl() + "/pid/" + session.getPid() + UNIFIED_MCP_PATH;
    }

    @Override
    public void dispose() {
        synchronized (lifecycleLock) {
            if (server != null) {
                server.stop(0);
                server = null;
            }
            if (executor != null) {
                executor.shutdownNow();
                executor = null;
            }
            boundPort = -1;
        }
    }

    private void ensureStarted() {
        synchronized (lifecycleLock) {
            int requestedPort = preferredPortSupplier.getAsInt();
            if (server != null && preferredPort == requestedPort) {
                return;
            }
            dispose();
            try {
                int port = resolveGatewayPort(requestedPort);
                server = HttpServer.create(new InetSocketAddress(HOST, port), 0);
                executor = createExecutor();
                server.setExecutor(executor);
                server.createContext(BASE_PATH, this::handleRequest);
                server.start();
                boundPort = server.getAddress().getPort();
                preferredPort = requestedPort;
            } catch (IOException exception) {
                throw new IllegalStateException(
                        message("service.gateway.error.start_failed", exception.getMessage()), exception);
            }
        }
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        try {
            addCommonResponseHeaders(exchange.getResponseHeaders());
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!isAuthorized(exchange)) {
                writeJson(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            String suffix = path.length() <= BASE_PATH.length() ? "/" : path.substring(BASE_PATH.length());
            if ("/".equals(suffix) || suffix.isBlank()) {
                writeJson(exchange, 200, buildGatewayInfoJson());
                return;
            }
            if ("/sessions".equals(suffix)) {
                writeJson(exchange, 200, buildSessionsJson());
                return;
            }
            if (UNIFIED_MCP_PATH.equals(suffix)) {
                handleUnifiedMcp(exchange);
                return;
            }
            if (suffix.startsWith("/pid/") && suffix.endsWith(UNIFIED_MCP_PATH)) {
                String pidSegment = suffix.substring("/pid/".length(), suffix.length() - UNIFIED_MCP_PATH.length());
                long pid = Long.parseLong(pidSegment);
                GatewaySessionTarget target = sessionRegistry.findByPid(pid);
                proxyToSession(exchange, target);
                return;
            }
            if (suffix.startsWith("/session/") && suffix.endsWith(UNIFIED_MCP_PATH)) {
                String sessionId = suffix.substring("/session/".length(), suffix.length() - UNIFIED_MCP_PATH.length());
                GatewaySessionTarget target = sessionRegistry.findBySessionId(sessionId);
                proxyToSession(exchange, target);
                return;
            }
            writeJson(exchange, 404, "{\"error\":\"unknown_path\"}");
        } catch (NumberFormatException exception) {
            writeJson(exchange, 400, "{\"error\":\"invalid_pid\"}");
        } catch (JsonParseException exception) {
            writeJson(exchange, 400, jsonError("invalid_json"));
        } catch (IllegalStateException exception) {
            writeJson(exchange, 502, jsonError(exception.getMessage()));
        } catch (Exception exception) {
            writeJson(exchange, 500, jsonError(exception.getMessage()));
        } finally {
            exchange.close();
        }
    }

    /**
     * 统一 MCP 入口由网关自己实现 JSON-RPC 的最小能力集，再按会话路由到具体 Arthas agent。
     */
    private void handleUnifiedMcp(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(exchange, 405, jsonError("mcp_post_required"));
            return;
        }
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonObject request = parseJsonObject(requestBody);
        JsonElement id = cloneJson(request.get("id"));
        String method = stringValue(request.get("method"));
        exchange.getResponseHeaders().set(MCP_SESSION_HEADER, UNIFIED_MCP_SESSION_ID);
        if (method.isBlank()) {
            writeJson(exchange, 200, GSON.toJson(buildJsonRpcError(id, -32600, "Missing MCP method.")));
            return;
        }
        try {
            switch (method) {
                case "initialize" -> writeJson(exchange, 200, GSON.toJson(buildInitializeResponse(id, request)));
                case "notifications/initialized" -> exchange.sendResponseHeaders(202, -1);
                case "ping" -> writeJson(exchange, 200, GSON.toJson(buildJsonRpcResult(id, new JsonObject())));
                case "tools/list" -> writeJson(exchange, 200, GSON.toJson(buildToolsListResponse(id)));
                case "tools/call" -> writeJson(exchange, 200, GSON.toJson(handleToolsCall(request, id)));
                default ->
                    writeJson(
                            exchange,
                            200,
                            GSON.toJson(buildJsonRpcError(id, -32601, "Unsupported MCP method: " + method)));
            }
        } catch (IllegalArgumentException exception) {
            writeJson(exchange, 200, GSON.toJson(buildJsonRpcError(id, -32602, exception.getMessage())));
        } catch (IllegalStateException exception) {
            writeJson(exchange, 200, GSON.toJson(buildJsonRpcError(id, -32000, exception.getMessage())));
        }
    }

    private JsonObject buildInitializeResponse(JsonElement id, JsonObject request) {
        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", extractProtocolVersion(request));
        JsonObject capabilities = new JsonObject();
        JsonObject tools = new JsonObject();
        tools.addProperty("listChanged", false);
        capabilities.add("tools", tools);
        result.add("capabilities", capabilities);

        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", UNIFIED_MCP_SERVER_NAME);
        serverInfo.addProperty("version", gatewayVersion());
        result.add("serverInfo", serverInfo);
        return buildJsonRpcResult(id, result);
    }

    private JsonObject buildToolsListResponse(JsonElement id) throws IOException {
        JsonArray tools = new JsonArray();
        tools.add(buildGatewaySessionsTool());
        GatewaySessionTarget sampleTarget = firstRunningTarget();
        if (sampleTarget != null) {
            JsonObject upstreamRequest = new JsonObject();
            upstreamRequest.addProperty("jsonrpc", "2.0");
            upstreamRequest.addProperty("id", "gateway-tools-list");
            upstreamRequest.addProperty("method", "tools/list");
            upstreamRequest.add("params", new JsonObject());
            JsonObject upstreamResponse = proxyJsonRpcToSession(sampleTarget, upstreamRequest);
            JsonArray upstreamTools =
                    asArray(asObject(upstreamResponse.get("result")).get("tools"));
            for (JsonElement toolElement : upstreamTools) {
                if (toolElement != null && toolElement.isJsonObject()) {
                    tools.add(augmentToolDefinition(toolElement.getAsJsonObject()));
                }
            }
        }

        JsonObject result = new JsonObject();
        result.add("tools", tools);
        return buildJsonRpcResult(id, result);
    }

    private JsonObject handleToolsCall(JsonObject request, JsonElement id) throws IOException {
        JsonObject params = asObject(request.get("params"));
        String toolName = stringValue(params.get("name"));
        if (toolName.isBlank()) {
            throw new IllegalArgumentException("Missing MCP tool name.");
        }
        JsonObject arguments = asObject(params.get("arguments"));
        if (GATEWAY_SESSIONS_TOOL.equals(toolName)) {
            return buildJsonRpcResult(id, buildGatewaySessionsToolResult(arguments));
        }

        GatewaySessionTarget target = resolveTarget(arguments);
        JsonObject routedRequest = request.deepCopy();
        JsonObject routedParams = asObject(routedRequest.get("params"));
        routedParams.addProperty("name", toolName);
        routedParams.add("arguments", sanitizeRoutedArguments(arguments));
        routedRequest.add("params", routedParams);
        return proxyJsonRpcToSession(target, routedRequest);
    }

    private JsonObject buildGatewaySessionsToolResult(JsonObject arguments) {
        boolean includeStopped = booleanValue(arguments.get("includeStopped"), true);
        JsonArray sessions = new JsonArray();
        StringBuilder textBuilder = new StringBuilder();
        for (GatewaySessionTarget target : sessionRegistry.listSessions()) {
            ArthasSessionService.SessionSnapshot snapshot = target.snapshot();
            ArthasSession session = snapshot.getSession();
            if (!includeStopped && session.getStatus() != SessionStatus.RUNNING) {
                continue;
            }
            sessions.add(buildSessionSummaryJson(target));
            if (textBuilder.length() > 0) {
                textBuilder.append('\n');
            }
            textBuilder
                    .append("[")
                    .append(session.getStatus().name())
                    .append("] pid=")
                    .append(session.getPid())
                    .append(" sessionId=")
                    .append(snapshot.getId())
                    .append(" ")
                    .append(session.getDisplayName());
        }

        JsonObject structuredContent = new JsonObject();
        structuredContent.addProperty("gatewayMcpUrl", getUnifiedGatewayMcpUrl());
        structuredContent.add("sessions", sessions);

        JsonObject textContent = new JsonObject();
        textContent.addProperty("type", "text");
        textContent.addProperty(
                "text", textBuilder.length() == 0 ? "No Arthas sessions found." : textBuilder.toString());

        JsonArray content = new JsonArray();
        content.add(textContent);

        JsonObject result = new JsonObject();
        result.add("content", content);
        result.add("structuredContent", structuredContent);
        return result;
    }

    private JsonObject buildGatewaySessionsTool() {
        JsonObject tool = new JsonObject();
        tool.addProperty("name", GATEWAY_SESSIONS_TOOL);
        tool.addProperty("title", "List Arthas Sessions");
        tool.addProperty("description", "列出当前 Gateway 可路由的 Arthas 会话，并返回可直接复用的 pid 与 sessionId。");

        JsonObject inputSchema = new JsonObject();
        inputSchema.addProperty("type", "object");
        JsonObject properties = new JsonObject();

        JsonObject includeStopped = new JsonObject();
        includeStopped.addProperty("type", "boolean");
        includeStopped.addProperty("description", "是否同时返回已停止的会话，默认 true。");
        properties.add("includeStopped", includeStopped);

        inputSchema.add("properties", properties);
        tool.add("inputSchema", inputSchema);
        return tool;
    }

    /**
     * 所有 Arthas 原生工具在 Gateway 下都会额外支持 `pid` / `sessionId` 两个路由参数。
     */
    private JsonObject augmentToolDefinition(JsonObject sourceTool) {
        JsonObject tool = sourceTool.deepCopy();
        String description = stringValue(tool.get("description"));
        if (description.isBlank()) {
            description = "通过 Arthas Gateway 调用。";
        }
        description += " 可额外传入 pid 或 sessionId 路由到指定 Arthas 会话；当仅有一个运行中的会话时可省略。";
        tool.addProperty("description", description);

        JsonObject inputSchema = asObject(tool.get("inputSchema"));
        if (inputSchema.entrySet().isEmpty()) {
            inputSchema.addProperty("type", "object");
        } else if (!inputSchema.has("type")) {
            inputSchema.addProperty("type", "object");
        }
        JsonObject properties = asObject(inputSchema.get("properties"));

        JsonObject pid = new JsonObject();
        pid.addProperty("type", "integer");
        pid.addProperty("description", "目标 Java 进程 PID；在多会话场景下推荐显式传入。");
        properties.add(ROUTE_ARGUMENT_PID, pid);

        JsonObject sessionId = new JsonObject();
        sessionId.addProperty("type", "string");
        sessionId.addProperty("description", "目标 Arthas 会话 ID；若同时传入 pid，将优先使用 sessionId。");
        properties.add(ROUTE_ARGUMENT_SESSION_ID, sessionId);

        inputSchema.add("properties", properties);
        tool.add("inputSchema", inputSchema);
        return tool;
    }

    private GatewaySessionTarget resolveTarget(JsonObject arguments) {
        String sessionId = stringValue(arguments.get(ROUTE_ARGUMENT_SESSION_ID)).trim();
        if (!sessionId.isBlank()) {
            GatewaySessionTarget target = sessionRegistry.findBySessionId(sessionId);
            if (target == null) {
                throw new IllegalArgumentException("Unknown Arthas sessionId: " + sessionId);
            }
            assertRunning(target);
            return target;
        }

        JsonElement pidElement = arguments.get(ROUTE_ARGUMENT_PID);
        if (pidElement != null && !pidElement.isJsonNull()) {
            long pid = longValue(pidElement);
            GatewaySessionTarget target = sessionRegistry.findByPid(pid);
            if (target == null) {
                throw new IllegalArgumentException("Unknown target pid: " + pid);
            }
            assertRunning(target);
            return target;
        }

        List<GatewaySessionTarget> runningTargets = runningTargets();
        if (runningTargets.isEmpty()) {
            throw new IllegalStateException("No running Arthas session is available.");
        }
        if (runningTargets.size() > 1) {
            throw new IllegalArgumentException(
                    "Multiple running Arthas sessions found; please specify pid or sessionId.");
        }
        return runningTargets.getFirst();
    }

    private List<GatewaySessionTarget> runningTargets() {
        List<GatewaySessionTarget> runningTargets = new ArrayList<>();
        for (GatewaySessionTarget target : sessionRegistry.listSessions()) {
            if (target.snapshot().getSession().getStatus() == SessionStatus.RUNNING) {
                runningTargets.add(target);
            }
        }
        return runningTargets;
    }

    private GatewaySessionTarget firstRunningTarget() {
        List<GatewaySessionTarget> runningTargets = runningTargets();
        return runningTargets.isEmpty() ? null : runningTargets.getFirst();
    }

    private void assertRunning(GatewaySessionTarget target) {
        if (target.snapshot().getSession().getStatus() != SessionStatus.RUNNING) {
            throw new IllegalStateException(
                    "Arthas session is not running: " + target.snapshot().getId());
        }
    }

    private JsonObject sanitizeRoutedArguments(JsonObject arguments) {
        JsonObject sanitized = arguments.deepCopy();
        sanitized.remove(ROUTE_ARGUMENT_PID);
        sanitized.remove(ROUTE_ARGUMENT_SESSION_ID);
        return sanitized;
    }

    /**
     * 每次调用前先向目标 agent 发起一次 initialize，确保下游流式 HTTP 会话已建立。
     */
    private JsonObject proxyJsonRpcToSession(GatewaySessionTarget target, JsonObject request) throws IOException {
        ArthasSession session = target.snapshot().getSession();
        UpstreamHandshake handshake = initializeUpstreamSession(session);
        HttpResponseData response = postJson(session, GSON.toJson(request), handshake.sessionId());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("Upstream Arthas MCP call failed with HTTP " + response.statusCode());
        }
        return extractJsonRpcPayload(response);
    }

    private UpstreamHandshake initializeUpstreamSession(ArthasSession session) throws IOException {
        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("id", "gateway-init-" + UUID.randomUUID());
        request.addProperty("method", "initialize");

        JsonObject params = new JsonObject();
        params.addProperty("protocolVersion", DEFAULT_PROTOCOL_VERSION);
        params.add("capabilities", new JsonObject());
        JsonObject clientInfo = new JsonObject();
        clientInfo.addProperty("name", UNIFIED_MCP_SERVER_NAME);
        clientInfo.addProperty("version", gatewayVersion());
        params.add("clientInfo", clientInfo);
        request.add("params", params);

        HttpResponseData response = postJson(session, GSON.toJson(request), "");
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("Failed to initialize upstream Arthas MCP, HTTP " + response.statusCode());
        }
        String sessionId = firstHeaderValue(response.headers(), MCP_SESSION_HEADER);
        if (sessionId.isBlank()) {
            sessionId = firstHeaderValue(response.headers(), MCP_SESSION_HEADER.toLowerCase(Locale.ROOT));
        }
        if (sessionId.isBlank()) {
            throw new IllegalStateException("Upstream Arthas MCP did not return an MCP session id.");
        }
        return new UpstreamHandshake(sessionId);
    }

    private HttpResponseData postJson(ArthasSession session, String requestBody, String upstreamSessionId)
            throws IOException {
        URL url = URI.create(session.getMcpUrl()).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setInstanceFollowRedirects(false);
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setRequestProperty("Accept", CONTENT_TYPE_ACCEPT);
        connection.setRequestProperty("Content-Type", CONTENT_TYPE_JSON);
        if (!session.getMcpPassword().isBlank()) {
            connection.setRequestProperty("Authorization", "Bearer " + session.getMcpPassword());
        }
        if (upstreamSessionId != null && !upstreamSessionId.isBlank()) {
            connection.setRequestProperty(MCP_SESSION_HEADER, upstreamSessionId);
        }
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(requestBody.getBytes(StandardCharsets.UTF_8));
        }
        int responseCode = connection.getResponseCode();
        Map<String, List<String>> responseHeaders = connection.getHeaderFields();
        try (InputStream inputStream =
                responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
            String responseBody =
                    inputStream == null ? "" : new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return new HttpResponseData(responseCode, responseHeaders, responseBody);
        } finally {
            connection.disconnect();
        }
    }

    private void proxyToSession(HttpExchange exchange, GatewaySessionTarget target) throws IOException {
        if (target == null || target.snapshot().getSession().getStatus() != SessionStatus.RUNNING) {
            writeJson(exchange, 404, "{\"error\":\"session_not_found\"}");
            return;
        }
        ArthasSession session = target.snapshot().getSession();
        URL url = appendQuery(session.getMcpUrl(), exchange.getRequestURI()).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(exchange.getRequestMethod());
        connection.setInstanceFollowRedirects(false);
        connection.setDoInput(true);
        copyRequestHeaders(exchange.getRequestHeaders(), connection, session);

        byte[] requestBody = exchange.getRequestBody().readAllBytes();
        if (requestBody.length > 0) {
            connection.setDoOutput(true);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(requestBody);
            }
        }

        int responseCode = connection.getResponseCode();
        copyResponseHeaders(connection, exchange.getResponseHeaders());
        exchange.sendResponseHeaders(responseCode, 0);
        try (InputStream inputStream = responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
                OutputStream responseBody = exchange.getResponseBody()) {
            if (inputStream != null) {
                inputStream.transferTo(responseBody);
            }
        } finally {
            connection.disconnect();
        }
    }

    private void copyRequestHeaders(Headers sourceHeaders, HttpURLConnection connection, ArthasSession session) {
        for (Map.Entry<String, List<String>> entry : sourceHeaders.entrySet()) {
            String headerName = entry.getKey();
            if (headerName == null || isHopByHopHeader(headerName) || "Authorization".equalsIgnoreCase(headerName)) {
                continue;
            }
            for (String value : entry.getValue()) {
                connection.addRequestProperty(headerName, value);
            }
        }
        if (!session.getMcpPassword().isBlank()) {
            connection.setRequestProperty("Authorization", "Bearer " + session.getMcpPassword());
        }
    }

    private void copyResponseHeaders(HttpURLConnection connection, Headers targetHeaders) {
        for (Map.Entry<String, List<String>> entry :
                connection.getHeaderFields().entrySet()) {
            String headerName = entry.getKey();
            if (headerName == null || isHopByHopHeader(headerName)) {
                continue;
            }
            targetHeaders.put(headerName, new ArrayList<>(entry.getValue()));
        }
    }

    private void addCommonResponseHeaders(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Headers", "*");
        headers.set("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
    }

    private void writeJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE_JSON);
        exchange.sendResponseHeaders(statusCode, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        }
    }

    private String buildGatewayInfoJson() {
        return "{"
                + "\"name\":\"" + escapeJson(message("session.gateway.name")) + "\","
                + "\"baseUrl\":\"" + escapeJson(getBaseUrl()) + "\","
                + "\"sessionsUrl\":\"" + escapeJson(getSessionsUrl()) + "\","
                + "\"mcpUrl\":\"" + escapeJson(getUnifiedGatewayMcpUrl()) + "\","
                + "\"authorizationRequired\":" + (!gatewayToken().isBlank())
                + "}";
    }

    String buildSessionsJson() {
        List<GatewaySessionTarget> targets = sessionRegistry.listSessions();
        StringBuilder builder = new StringBuilder();
        builder.append("{\"sessions\":[");
        for (int i = 0; i < targets.size(); i++) {
            GatewaySessionTarget target = targets.get(i);
            ArthasSessionService.SessionSnapshot snapshot = target.snapshot();
            ArthasSession session = snapshot.getSession();
            if (i > 0) {
                builder.append(',');
            }
            builder.append('{');
            builder.append("\"project\":\"")
                    .append(escapeJson(target.projectName()))
                    .append("\",");
            builder.append("\"sessionId\":\"")
                    .append(escapeJson(snapshot.getId()))
                    .append("\",");
            builder.append("\"title\":\"")
                    .append(escapeJson(snapshot.getTitle()))
                    .append("\",");
            builder.append("\"pid\":").append(session.getPid()).append(',');
            builder.append("\"displayName\":\"")
                    .append(escapeJson(session.getDisplayName()))
                    .append("\",");
            builder.append("\"status\":\"").append(session.getStatus().name()).append("\",");
            builder.append("\"directMcpUrl\":\"")
                    .append(escapeJson(session.getMcpUrl()))
                    .append("\",");
            builder.append("\"gatewayMcpUrl\":\"")
                    .append(escapeJson(getUnifiedGatewayMcpUrl()))
                    .append("\",");
            builder.append("\"gatewayPidMcpUrl\":\"")
                    .append(escapeJson(getGatewayMcpUrl(session)))
                    .append("\",");
            builder.append("\"gatewaySessionUrl\":\"")
                    .append(escapeJson(getBaseUrl() + "/session/" + snapshot.getId() + UNIFIED_MCP_PATH))
                    .append("\",");
            builder.append("\"gatewayAuthorizationRequired\":")
                    .append(!gatewayToken().isBlank());
            builder.append('}');
        }
        builder.append("]}");
        return builder.toString();
    }

    private boolean isAuthorized(HttpExchange exchange) {
        String token = gatewayToken();
        if (token.isBlank()) {
            return true;
        }
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        return ("Bearer " + token).equals(authorization);
    }

    private String gatewayToken() {
        if (tokenSupplier == null) {
            return "";
        }
        String token = tokenSupplier.get();
        return token == null ? "" : token.trim();
    }

    private static String message(String key, Object... params) {
        return ArthasWorkbenchBundle.message(key, params);
    }

    private static URI appendQuery(String rawUrl, URI incomingUri) {
        String query = incomingUri.getRawQuery();
        if (query == null || query.isBlank()) {
            return URI.create(rawUrl);
        }
        String joiner = rawUrl.contains("?") ? "&" : "?";
        return URI.create(rawUrl + joiner + query);
    }

    private static boolean isHopByHopHeader(String headerName) {
        String normalized = headerName.toLowerCase(Locale.ROOT);
        return normalized.equals("host")
                || normalized.equals("connection")
                || normalized.equals("content-length")
                || normalized.equals("transfer-encoding")
                || normalized.equals("keep-alive")
                || normalized.equals("upgrade");
    }

    private static int resolveGatewayPort(int requestedPort) {
        if (requestedPort == 0) {
            return 0;
        }
        Integer preferred = requestedPort > 0 ? requestedPort : DEFAULT_GATEWAY_PORT;
        return PortToolkit.resolvePort(preferred, DEFAULT_GATEWAY_PORT, PortAllocationMode.PREFER_CONFIGURED);
    }

    private static int parseGatewayPort(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_GATEWAY_PORT;
        }
        try {
            int port = Integer.parseInt(value.trim());
            return port >= 0 && port <= 65535 ? port : DEFAULT_GATEWAY_PORT;
        } catch (NumberFormatException exception) {
            return DEFAULT_GATEWAY_PORT;
        }
    }

    private static ExecutorService createExecutor() {
        AtomicLong sequence = new AtomicLong();
        return Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "arthas-mcp-gateway-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    private static JsonObject parseJsonObject(String body) {
        JsonElement element = JsonParser.parseString(body);
        if (!element.isJsonObject()) {
            throw new JsonParseException("json_object_required");
        }
        return element.getAsJsonObject();
    }

    /**
     * Arthas MCP 的部分工具调用会返回 SSE，而不是普通 JSON。
     * 这里统一兼容两种响应体，避免 Gateway 在固定入口下转发工具调用时报 invalid_json。
     */
    private static JsonObject extractJsonRpcPayload(HttpResponseData response) {
        String body = response.body() == null ? "" : response.body().trim();
        if (body.isBlank()) {
            throw new JsonParseException("empty_json_rpc_response");
        }
        if (isEventStreamResponse(response, body)) {
            return parseJsonObject(extractSseDataPayload(body));
        }
        return parseJsonObject(body);
    }

    private static boolean isEventStreamResponse(HttpResponseData response, String body) {
        String contentType = firstHeaderValue(response.headers(), "Content-Type");
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        return normalizedContentType.contains("text/event-stream")
                || body.startsWith("data:")
                || body.startsWith("event:")
                || body.startsWith("id:");
    }

    /**
     * 只提取首个完整 SSE 事件里的 data 段，并将多行 data 拼接成一个 JSON 字符串。
     */
    private static String extractSseDataPayload(String body) {
        StringBuilder dataBuilder = new StringBuilder();
        boolean collectingData = false;
        String[] lines = body.split("\\R", -1);
        for (String line : lines) {
            if (line.startsWith("data:")) {
                if (dataBuilder.length() > 0) {
                    dataBuilder.append('\n');
                }
                dataBuilder.append(trimSseFieldValue(line.substring("data:".length())));
                collectingData = true;
                continue;
            }
            if (line.isBlank() && collectingData) {
                return dataBuilder.toString();
            }
        }
        if (collectingData && dataBuilder.length() > 0) {
            return dataBuilder.toString();
        }
        throw new JsonParseException("sse_json_payload_required");
    }

    private static String trimSseFieldValue(String rawValue) {
        return rawValue.startsWith(" ") ? rawValue.substring(1) : rawValue;
    }

    private static String gatewayVersion() {
        String version = ArthasMcpGatewayService.class.getPackage().getImplementationVersion();
        return version == null || version.isBlank() ? "dev" : version;
    }

    private static String extractProtocolVersion(JsonObject request) {
        JsonObject params = asObject(request.get("params"));
        String version = stringValue(params.get("protocolVersion"));
        return version.isBlank() ? DEFAULT_PROTOCOL_VERSION : version;
    }

    private static JsonObject buildJsonRpcResult(JsonElement id, JsonElement result) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", cloneJson(id));
        response.add("result", result == null ? JsonNull.INSTANCE : cloneJson(result));
        return response;
    }

    private static JsonObject buildJsonRpcError(JsonElement id, int code, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", cloneJson(id));
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message == null ? "unknown_error" : message);
        response.add("error", error);
        return response;
    }

    private JsonObject buildSessionSummaryJson(GatewaySessionTarget target) {
        ArthasSessionService.SessionSnapshot snapshot = target.snapshot();
        ArthasSession session = snapshot.getSession();

        JsonObject summary = new JsonObject();
        summary.addProperty("project", target.projectName());
        summary.addProperty("sessionId", snapshot.getId());
        summary.addProperty("title", snapshot.getTitle());
        summary.addProperty("pid", session.getPid());
        summary.addProperty("displayName", session.getDisplayName());
        summary.addProperty("status", session.getStatus().name());
        summary.addProperty("directMcpUrl", session.getMcpUrl());
        summary.addProperty("gatewayMcpUrl", getUnifiedGatewayMcpUrl());
        summary.addProperty("gatewayPidMcpUrl", getGatewayMcpUrl(session));
        summary.addProperty("gatewaySessionUrl", getBaseUrl() + "/session/" + snapshot.getId() + UNIFIED_MCP_PATH);

        JsonObject routeArguments = new JsonObject();
        routeArguments.addProperty(ROUTE_ARGUMENT_PID, session.getPid());
        routeArguments.addProperty(ROUTE_ARGUMENT_SESSION_ID, snapshot.getId());
        summary.add("routeArguments", routeArguments);
        return summary;
    }

    private static JsonObject asObject(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonObject()) {
            return new JsonObject();
        }
        return element.getAsJsonObject();
    }

    private static JsonArray asArray(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonArray()) {
            return new JsonArray();
        }
        return element.getAsJsonArray();
    }

    private static JsonElement cloneJson(JsonElement element) {
        return element == null ? JsonNull.INSTANCE : element.deepCopy();
    }

    private static String stringValue(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        return element.getAsString();
    }

    private static boolean booleanValue(JsonElement element, boolean defaultValue) {
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        return element.getAsBoolean();
    }

    private static long longValue(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            throw new IllegalArgumentException("pid is required.");
        }
        if (element.getAsJsonPrimitive().isNumber()) {
            return element.getAsLong();
        }
        String text = element.getAsString().trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException("pid is required.");
        }
        return Long.parseLong(text);
    }

    private static String firstHeaderValue(Map<String, List<String>> headers, String headerName) {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String key = entry.getKey();
            if (key != null
                    && key.equalsIgnoreCase(headerName)
                    && entry.getValue() != null
                    && !entry.getValue().isEmpty()) {
                return entry.getValue().getFirst();
            }
        }
        return "";
    }

    private static String escapeJson(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> builder.append(current);
            }
        }
        return builder.toString();
    }

    private static String jsonError(String message) {
        return "{\"error\":\"" + escapeJson(message == null ? "unknown_error" : message) + "\"}";
    }

    /**
     * 会话索引抽象，方便在测试中注入伪造数据源。
     */
    interface SessionRegistry {
        List<GatewaySessionTarget> listSessions();

        GatewaySessionTarget findBySessionId(String sessionId);

        GatewaySessionTarget findByPid(long pid);
    }

    /**
     * 描述 Gateway 暴露给外部的单个会话目标。
     */
    record GatewaySessionTarget(String projectName, ArthasSessionService.SessionSnapshot snapshot) {
        GatewaySessionTarget {
            Objects.requireNonNull(projectName, "projectName");
            Objects.requireNonNull(snapshot, "snapshot");
        }
    }

    private record HttpResponseData(int statusCode, Map<String, List<String>> headers, String body) {}

    private record UpstreamHandshake(String sessionId) {}

    /**
     * 默认注册表实现，遍历当前所有打开项目并收集其中的 Arthas 会话。
     */
    private static final class LiveSessionRegistry implements SessionRegistry {
        @Override
        public List<GatewaySessionTarget> listSessions() {
            List<GatewaySessionTarget> targets = new ArrayList<>();
            for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                if (project.isDisposed()) {
                    continue;
                }
                ArthasSessionService sessionService = project.getService(ArthasSessionService.class);
                if (sessionService == null) {
                    continue;
                }
                for (ArthasSessionService.SessionSnapshot snapshot : sessionService.snapshots()) {
                    targets.add(new GatewaySessionTarget(project.getName(), snapshot));
                }
            }
            return targets;
        }

        @Override
        public GatewaySessionTarget findBySessionId(String sessionId) {
            for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                if (project.isDisposed()) {
                    continue;
                }
                ArthasSessionService sessionService = project.getService(ArthasSessionService.class);
                if (sessionService == null) {
                    continue;
                }
                ArthasSessionService.SessionSnapshot snapshot = sessionService.findSnapshot(sessionId);
                if (snapshot != null) {
                    return new GatewaySessionTarget(project.getName(), snapshot);
                }
            }
            return null;
        }

        @Override
        public GatewaySessionTarget findByPid(long pid) {
            for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                if (project.isDisposed()) {
                    continue;
                }
                ArthasSessionService sessionService = project.getService(ArthasSessionService.class);
                if (sessionService == null) {
                    continue;
                }
                ArthasSessionService.SessionSnapshot snapshot = sessionService.findLatestByPid(pid);
                if (snapshot != null) {
                    return new GatewaySessionTarget(project.getName(), snapshot);
                }
            }
            return null;
        }
    }
}
