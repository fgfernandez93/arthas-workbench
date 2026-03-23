package com.alibaba.arthas.idea.workbench.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.alibaba.arthas.idea.workbench.model.ArthasSession;
import com.alibaba.arthas.idea.workbench.model.ArthasSessionViewType;
import com.alibaba.arthas.idea.workbench.model.SessionStatus;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/**
 * {@link ArthasMcpGatewayService} 的网关转发、统一 MCP 路由与认证测试。
 */
public class ArthasMcpGatewayServiceTest {

    @Test
    /**
     * 验证旧的按 PID 路由入口仍然可用，并且会话列表会同时暴露新的统一 MCP 地址。
     */
    public void shouldExposeSessionListAndKeepLegacyPidProxy() throws Exception {
        AtomicReference<String> authorizationHeader = new AtomicReference<>("");
        AtomicReference<String> requestBody = new AtomicReference<>("");
        HttpServer downstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        downstream.createContext("/mcp", exchange -> {
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] payload = "{\"result\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(payload);
            }
        });
        downstream.start();

        try {
            ArthasSession session = new ArthasSession(
                    "session-1",
                    4567L,
                    "demo-app",
                    downstream.getAddress().getPort(),
                    3658,
                    "/mcp",
                    "secret",
                    "官方最新版本",
                    "Arthas Boot",
                    "/tmp/java",
                    "/tmp/arthas-boot.jar",
                    "/tmp/arthas",
                    SessionStatus.RUNNING);
            ArthasMcpGatewayService gatewayService = new ArthasMcpGatewayService(
                    new StaticSessionRegistry(List.of(target("demo-project", session))), () -> 0);

            try {
                String sessionsJson = readText(new URL(gatewayService.getSessionsUrl()));
                assertTrue(sessionsJson.contains("\"pid\":4567"));
                assertTrue(sessionsJson.contains("\"gatewayMcpUrl\":\"" + gatewayService.getUnifiedGatewayMcpUrl()));
                assertTrue(sessionsJson.contains("\"gatewayPidMcpUrl\""));

                URL proxyUrl = new URL(gatewayService.getGatewayMcpUrl(session));
                HttpURLConnection connection = (HttpURLConnection) proxyUrl.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                try (OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write("{\"ping\":true}".getBytes(StandardCharsets.UTF_8));
                }

                assertEquals(200, connection.getResponseCode());
                String responseBody = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(responseBody.contains("\"result\":\"ok\""));
                assertEquals("Bearer secret", authorizationHeader.get());
                assertEquals("{\"ping\":true}", requestBody.get());
            } finally {
                gatewayService.dispose();
            }
        } finally {
            downstream.stop(0);
        }
    }

    @Test
    /**
     * 验证统一 MCP 固定 URL 可以完成 initialize 与 tools/list，并为 Arthas 工具注入路由参数。
     */
    public void shouldExposeUnifiedMcpUrlAndAggregateTools() throws Exception {
        try (MockArthasMcpServer downstream = new MockArthasMcpServer("server-a")) {
            ArthasSession session =
                    downstream.createSession("session-1", 4567L, "demo-app", "secret", SessionStatus.RUNNING);
            ArthasMcpGatewayService gatewayService = new ArthasMcpGatewayService(
                    new StaticSessionRegistry(List.of(target("demo-project", session))), () -> 0);

            try {
                SimpleHttpResponse initialize = postJson(
                        gatewayService.getUnifiedGatewayMcpUrl(),
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0.0\"}}}",
                        "");
                assertEquals(200, initialize.statusCode());
                assertEquals("idea-arthas-workbench-gateway", initialize.firstHeader("Mcp-Session-Id"));
                assertTrue(initialize.body().contains("\"name\":\"idea-arthas-workbench\""));

                SimpleHttpResponse toolsList = postJson(
                        gatewayService.getUnifiedGatewayMcpUrl(),
                        "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}",
                        "");
                assertEquals(200, toolsList.statusCode());

                JsonArray tools = JsonParser.parseString(toolsList.body())
                        .getAsJsonObject()
                        .getAsJsonObject("result")
                        .getAsJsonArray("tools");
                JsonObject gatewaySessionsTool = findTool(tools, "gateway_sessions");
                JsonObject jvmTool = findTool(tools, "jvm");

                assertNotNull(gatewaySessionsTool);
                assertNotNull(jvmTool);
                assertTrue(jvmTool.toString().contains("\"pid\""));
                assertTrue(jvmTool.toString().contains("\"sessionId\""));
                assertEquals(1, downstream.initializeCount.get());
                assertEquals(1, downstream.toolsListCount.get());
            } finally {
                gatewayService.dispose();
            }
        }
    }

    @Test
    /**
     * 验证仅存在一个运行中的会话时，统一 MCP 会自动路由到该会话，无需显式传 pid。
     */
    public void shouldRouteUnifiedToolCallToSingleRunningSession() throws Exception {
        try (MockArthasMcpServer downstream = new MockArthasMcpServer("server-a")) {
            ArthasSession session =
                    downstream.createSession("session-1", 4567L, "demo-app", "secret", SessionStatus.RUNNING);
            ArthasMcpGatewayService gatewayService = new ArthasMcpGatewayService(
                    new StaticSessionRegistry(List.of(target("demo-project", session))), () -> 0);

            try {
                SimpleHttpResponse response = postJson(
                        gatewayService.getUnifiedGatewayMcpUrl(),
                        "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"jvm\",\"arguments\":{\"verbose\":true}}}",
                        "");
                assertEquals(200, response.statusCode());
                assertTrue(response.body().contains("server-a:tools/call"));
                assertEquals("Bearer secret", downstream.authorizationHeader.get());
                assertEquals(1, downstream.initializeCount.get());
                assertEquals(1, downstream.toolsCallCount.get());
                assertEquals("mock-session-server-a", downstream.lastToolCallSessionId.get());

                JsonObject forwardedRequest = JsonParser.parseString(downstream.lastToolCallBody.get())
                        .getAsJsonObject();
                JsonObject forwardedArguments =
                        forwardedRequest.getAsJsonObject("params").getAsJsonObject("arguments");
                assertTrue(forwardedArguments.get("verbose").getAsBoolean());
                assertFalse(forwardedArguments.has("pid"));
                assertFalse(forwardedArguments.has("sessionId"));
            } finally {
                gatewayService.dispose();
            }
        }
    }

    @Test
    /**
     * 验证固定统一 MCP 入口在上游返回 SSE 时，仍然可以正确提取 JSON-RPC 结果。
     */
    public void shouldParseSseToolCallResponseFromUnifiedGateway() throws Exception {
        try (MockArthasMcpServer downstream = new MockArthasMcpServer("server-sse", true)) {
            ArthasSession session =
                    downstream.createSession("session-1", 4567L, "demo-app", "secret", SessionStatus.RUNNING);
            ArthasMcpGatewayService gatewayService = new ArthasMcpGatewayService(
                    new StaticSessionRegistry(List.of(target("demo-project", session))), () -> 0);

            try {
                SimpleHttpResponse response = postJson(
                        gatewayService.getUnifiedGatewayMcpUrl(),
                        "{\"jsonrpc\":\"2.0\",\"id\":33,\"method\":\"tools/call\",\"params\":{\"name\":\"jvm\",\"arguments\":{\"verbose\":true}}}",
                        "");
                assertEquals(200, response.statusCode());

                JsonObject result = JsonParser.parseString(response.body())
                        .getAsJsonObject()
                        .getAsJsonObject("result");
                JsonArray content = result.getAsJsonArray("content");
                assertEquals(1, downstream.toolsCallCount.get());
                assertEquals(
                        "text", content.get(0).getAsJsonObject().get("type").getAsString());
                assertTrue(content.get(0)
                        .getAsJsonObject()
                        .get("text")
                        .getAsString()
                        .contains("server-sse"));
            } finally {
                gatewayService.dispose();
            }
        }
    }

    @Test
    /**
     * 验证多会话场景下如果不显式指定 pid 或 sessionId，会返回明确的路由错误。
     */
    public void shouldRequireExplicitTargetWhenMultipleRunningSessions() throws Exception {
        ArthasSession first = createSession("session-1", 1111L, "first-app", 8563, SessionStatus.RUNNING);
        ArthasSession second = createSession("session-2", 2222L, "second-app", 8564, SessionStatus.RUNNING);
        ArthasMcpGatewayService gatewayService = new ArthasMcpGatewayService(
                new StaticSessionRegistry(List.of(target("demo-project", first), target("demo-project", second))),
                () -> 0);

        try {
            SimpleHttpResponse response = postJson(
                    gatewayService.getUnifiedGatewayMcpUrl(),
                    "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"jvm\",\"arguments\":{}}}",
                    "");
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"code\":-32602"));
            assertTrue(response.body().contains("Multiple running Arthas sessions found"));
        } finally {
            gatewayService.dispose();
        }
    }

    @Test
    /**
     * 验证统一 MCP 可以通过 pid 将工具调用路由到指定会话。
     */
    public void shouldRouteUnifiedToolCallByPid() throws Exception {
        try (MockArthasMcpServer first = new MockArthasMcpServer("server-a");
                MockArthasMcpServer second = new MockArthasMcpServer("server-b")) {
            ArthasSession firstSession =
                    first.createSession("session-1", 1111L, "first-app", "secret-a", SessionStatus.RUNNING);
            ArthasSession secondSession =
                    second.createSession("session-2", 2222L, "second-app", "secret-b", SessionStatus.RUNNING);
            ArthasMcpGatewayService gatewayService = new ArthasMcpGatewayService(
                    new StaticSessionRegistry(
                            List.of(target("demo-project", firstSession), target("demo-project", secondSession))),
                    () -> 0);

            try {
                SimpleHttpResponse response = postJson(
                        gatewayService.getUnifiedGatewayMcpUrl(),
                        "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{\"name\":\"jvm\",\"arguments\":{\"pid\":2222,\"verbose\":true}}}",
                        "");
                assertEquals(200, response.statusCode());
                assertTrue(response.body().contains("server-b:tools/call"));
                assertEquals(0, first.toolsCallCount.get());
                assertEquals(1, second.toolsCallCount.get());
                assertEquals("Bearer secret-b", second.authorizationHeader.get());
            } finally {
                gatewayService.dispose();
            }
        }
    }

    @Test
    /**
     * 验证调用 gateway_sessions 工具时会返回结构化的会话列表与统一入口地址。
     */
    public void shouldReturnStructuredSessionsForGatewayTool() throws Exception {
        ArthasSession running = createSession("session-1", 1111L, "first-app", 8563, SessionStatus.RUNNING);
        ArthasSession stopped = createSession("session-2", 2222L, "second-app", 8564, SessionStatus.STOPPED);
        ArthasMcpGatewayService gatewayService = new ArthasMcpGatewayService(
                new StaticSessionRegistry(List.of(target("demo-project", running), target("demo-project", stopped))),
                () -> 0);

        try {
            SimpleHttpResponse response = postJson(
                    gatewayService.getUnifiedGatewayMcpUrl(),
                    "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\",\"params\":{\"name\":\"gateway_sessions\",\"arguments\":{\"includeStopped\":true}}}",
                    "");
            assertEquals(200, response.statusCode());

            JsonObject structuredContent = JsonParser.parseString(response.body())
                    .getAsJsonObject()
                    .getAsJsonObject("result")
                    .getAsJsonObject("structuredContent");
            JsonArray sessions = structuredContent.getAsJsonArray("sessions");

            assertEquals(
                    gatewayService.getUnifiedGatewayMcpUrl(),
                    structuredContent.get("gatewayMcpUrl").getAsString());
            assertEquals(2, sessions.size());
            assertTrue(sessions.toString().contains("\"routeArguments\""));
            assertTrue(sessions.toString().contains("\"gatewayPidMcpUrl\""));
        } finally {
            gatewayService.dispose();
        }
    }

    @Test
    /**
     * 验证配置网关 token 后，未携带 Authorization 的请求会被拒绝。
     */
    public void shouldRequireGatewayTokenWhenConfigured() throws Exception {
        ArthasSession session = new ArthasSession(
                "session-1",
                4567L,
                "demo-app",
                8563,
                3658,
                "/mcp",
                "",
                "官方最新版本",
                "Arthas Boot",
                "/tmp/java",
                "/tmp/arthas-boot.jar",
                "/tmp/arthas",
                SessionStatus.RUNNING);
        ArthasSessionService.SessionSnapshot snapshot = new ArthasSessionService.SessionSnapshot(
                "session-1", "PID 4567 #1", session, "", true, ArthasSessionViewType.LOG);
        ArthasMcpGatewayService gatewayService = new ArthasMcpGatewayService(
                new StaticSessionRegistry(
                        List.of(new ArthasMcpGatewayService.GatewaySessionTarget("demo-project", snapshot))),
                () -> 0,
                () -> "gateway-secret");

        try {
            URL sessionsUrl = new URL(gatewayService.getSessionsUrl());
            HttpURLConnection unauthorized = (HttpURLConnection) sessionsUrl.openConnection();
            assertEquals(401, unauthorized.getResponseCode());

            HttpURLConnection authorized = (HttpURLConnection) sessionsUrl.openConnection();
            authorized.setRequestProperty("Authorization", "Bearer gateway-secret");
            assertEquals(200, authorized.getResponseCode());
            assertTrue(new String(authorized.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .contains("\"sessions\""));
        } finally {
            gatewayService.dispose();
        }
    }

    /**
     * 简化读取 HTTP 文本响应的辅助方法。
     */
    private static String readText(URL url) throws IOException {
        try (InputStream inputStream = url.openStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 统一发送 JSON 请求，避免各个测试重复编写 HTTP 样板代码。
     */
    private static SimpleHttpResponse postJson(String url, String body, String authorization) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Accept", "application/json, text/event-stream");
        connection.setRequestProperty("Content-Type", "application/json");
        if (authorization != null && !authorization.isBlank()) {
            connection.setRequestProperty("Authorization", authorization);
        }
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int responseCode = connection.getResponseCode();
        Map<String, List<String>> headers = connection.getHeaderFields();
        try (InputStream inputStream =
                responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
            String responseBody =
                    inputStream == null ? "" : new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return new SimpleHttpResponse(responseCode, headers, responseBody);
        } finally {
            connection.disconnect();
        }
    }

    /**
     * 创建测试用会话，未真正启动下游服务时仅用于本地路由与错误分支验证。
     */
    private static ArthasSession createSession(
            String id, long pid, String displayName, int httpPort, SessionStatus status) {
        return new ArthasSession(
                id,
                pid,
                displayName,
                httpPort,
                3658,
                "/mcp",
                "",
                "官方最新版本",
                "Arthas Boot",
                "/tmp/java",
                "/tmp/arthas-boot.jar",
                "/tmp/arthas",
                status);
    }

    /**
     * 创建测试用 Gateway 目标对象，保持和真实 UI 层相同的数据结构。
     */
    private static ArthasMcpGatewayService.GatewaySessionTarget target(String projectName, ArthasSession session) {
        ArthasSessionService.SessionSnapshot snapshot = new ArthasSessionService.SessionSnapshot(
                session.getId(), "PID " + session.getPid() + " #1", session, "", true, ArthasSessionViewType.CONSOLE);
        return new ArthasMcpGatewayService.GatewaySessionTarget(projectName, snapshot);
    }

    /**
     * 从工具列表里按名称查找单个工具定义。
     */
    private static JsonObject findTool(JsonArray tools, String name) {
        for (int index = 0; index < tools.size(); index++) {
            JsonObject tool = tools.get(index).getAsJsonObject();
            if (name.equals(tool.get("name").getAsString())) {
                return tool;
            }
        }
        return null;
    }

    /**
     * 静态注册表用于测试中精确控制当前可见的会话列表。
     */
    private record StaticSessionRegistry(List<ArthasMcpGatewayService.GatewaySessionTarget> targets)
            implements ArthasMcpGatewayService.SessionRegistry {

        @Override
        public List<ArthasMcpGatewayService.GatewaySessionTarget> listSessions() {
            return new ArrayList<>(targets);
        }

        @Override
        public ArthasMcpGatewayService.GatewaySessionTarget findBySessionId(String sessionId) {
            for (ArthasMcpGatewayService.GatewaySessionTarget target : targets) {
                if (target.snapshot().getId().equals(sessionId)) {
                    return target;
                }
            }
            return null;
        }

        @Override
        public ArthasMcpGatewayService.GatewaySessionTarget findByPid(long pid) {
            for (ArthasMcpGatewayService.GatewaySessionTarget target : targets) {
                if (target.snapshot().getSession().getPid() == pid) {
                    return target;
                }
            }
            return null;
        }
    }

    /**
     * 模拟一个最小可用的 Arthas MCP 服务，用来验证 Gateway 的初始化、工具发现和工具调用链路。
     */
    private static final class MockArthasMcpServer implements AutoCloseable {
        private final HttpServer server;
        private final String label;
        private final String upstreamSessionId;
        private final boolean streamToolCallResponse;
        private final AtomicReference<String> authorizationHeader = new AtomicReference<>("");
        private final AtomicReference<String> lastToolCallBody = new AtomicReference<>("");
        private final AtomicReference<String> lastToolCallSessionId = new AtomicReference<>("");
        private final AtomicInteger initializeCount = new AtomicInteger();
        private final AtomicInteger toolsListCount = new AtomicInteger();
        private final AtomicInteger toolsCallCount = new AtomicInteger();

        private MockArthasMcpServer(String label) throws IOException {
            this(label, false);
        }

        private MockArthasMcpServer(String label, boolean streamToolCallResponse) throws IOException {
            this.label = label;
            this.streamToolCallResponse = streamToolCallResponse;
            this.upstreamSessionId = "mock-session-" + label;
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.server.createContext("/mcp", exchange -> {
                authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                JsonObject request = JsonParser.parseString(requestBody).getAsJsonObject();
                String method = request.get("method").getAsString();
                switch (method) {
                    case "initialize" -> {
                        initializeCount.incrementAndGet();
                        JsonObject result = new JsonObject();
                        result.addProperty("protocolVersion", "2025-03-26");
                        result.add("capabilities", new JsonObject());
                        JsonObject serverInfo = new JsonObject();
                        serverInfo.addProperty("name", "arthas-mcp-server");
                        serverInfo.addProperty("version", "4.1.8");
                        result.add("serverInfo", serverInfo);
                        writeJson(exchange, buildJsonRpcResponse(request.get("id"), result), upstreamSessionId);
                    }
                    case "tools/list" -> {
                        toolsListCount.incrementAndGet();
                        assertEquals(
                                upstreamSessionId, exchange.getRequestHeaders().getFirst("Mcp-Session-Id"));
                        JsonObject tool = new JsonObject();
                        tool.addProperty("name", "jvm");
                        tool.addProperty("description", "Inspect JVM");
                        JsonObject inputSchema = new JsonObject();
                        inputSchema.addProperty("type", "object");
                        JsonObject properties = new JsonObject();
                        JsonObject verbose = new JsonObject();
                        verbose.addProperty("type", "boolean");
                        properties.add("verbose", verbose);
                        inputSchema.add("properties", properties);
                        tool.add("inputSchema", inputSchema);

                        JsonArray tools = new JsonArray();
                        tools.add(tool);
                        JsonObject result = new JsonObject();
                        result.add("tools", tools);
                        writeJson(exchange, buildJsonRpcResponse(request.get("id"), result), "");
                    }
                    case "tools/call" -> {
                        toolsCallCount.incrementAndGet();
                        lastToolCallBody.set(requestBody);
                        lastToolCallSessionId.set(exchange.getRequestHeaders().getFirst("Mcp-Session-Id"));
                        JsonArray content = new JsonArray();
                        JsonObject text = new JsonObject();
                        text.addProperty("type", "text");
                        text.addProperty("text", label + ":tools/call");
                        content.add(text);
                        JsonObject result = new JsonObject();
                        result.add("content", content);
                        if (streamToolCallResponse) {
                            writeSse(exchange, buildJsonRpcResponse(request.get("id"), result));
                        } else {
                            writeJson(exchange, buildJsonRpcResponse(request.get("id"), result), "");
                        }
                    }
                    default -> {
                        JsonObject result = new JsonObject();
                        result.addProperty("message", "unsupported");
                        writeJson(exchange, buildJsonRpcResponse(request.get("id"), result), "");
                    }
                }
            });
            this.server.start();
        }

        private ArthasSession createSession(
                String id, long pid, String displayName, String password, SessionStatus status) {
            return new ArthasSession(
                    id,
                    pid,
                    displayName,
                    server.getAddress().getPort(),
                    3658,
                    "/mcp",
                    password,
                    "官方最新版本",
                    "Arthas Boot",
                    "/tmp/java",
                    "/tmp/arthas-boot.jar",
                    "/tmp/arthas",
                    status);
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private static void writeJson(HttpExchange exchange, JsonObject payload, String mcpSessionId)
                throws IOException {
            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (mcpSessionId != null && !mcpSessionId.isBlank()) {
                exchange.getResponseHeaders().set("Mcp-Session-Id", mcpSessionId);
            }
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        }

        private static void writeSse(HttpExchange exchange, JsonObject payload) throws IOException {
            String body = "id: mock-event\n" + "event: message\n" + "data: " + payload + "\n\n";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        }

        private static JsonObject buildJsonRpcResponse(JsonElement id, JsonObject result) {
            JsonObject response = new JsonObject();
            response.addProperty("jsonrpc", "2.0");
            response.add("id", id);
            response.add("result", result);
            return response;
        }
    }

    /**
     * 记录一次 HTTP 调用结果，方便读取头和正文。
     */
    private record SimpleHttpResponse(int statusCode, Map<String, List<String>> headers, String body) {

        private String firstHeader(String headerName) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey() != null
                        && entry.getKey().equalsIgnoreCase(headerName)
                        && entry.getValue() != null
                        && !entry.getValue().isEmpty()) {
                    return entry.getValue().get(0);
                }
            }
            return "";
        }
    }
}
