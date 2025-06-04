package mcp.java_mcp_server.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class McpWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        System.out.println("MCP 클라이언트 연결됨: " + session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        System.out.println("MCP 클라이언트 연결 종료됨: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            JsonNode request = objectMapper.readTree(message.getPayload());
            Map<String, Object> response = processRequest(request);

            String responseJson = objectMapper.writeValueAsString(response);
            session.sendMessage(new TextMessage(responseJson));

        } catch (Exception e) {
            sendErrorResponse(session, "메시지 처리 오류: " + e.getMessage());
        }
    }

    private Map<String, Object> processRequest(JsonNode request) {
        String method = request.get("method").asText();
        String id = request.has("id") ? request.get("id").asText() : null;

        switch (method) {
            case "initialize":
                return handleInitialize(request, id);
            case "tools/list":
                return handleListTools(id);
            case "tools/call":
                return handleCallTool(request, id);
            case "notifications/initialized":
                return handleInitialized(id);
            default:
                return createErrorResponse(id, "Unknown method: " + method);
        }
    }

    private Map<String, Object> handleInitialize(JsonNode request, String id) {
        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);

        Map<String, Object> result = new HashMap<>();
        result.put("protocolVersion", "2024-11-05");
        result.put("capabilities", Map.of(
                "tools", Map.of("listChanged", true),
                "resources", Map.of("subscribe", true, "listChanged", true)
        ));
        result.put("serverInfo", Map.of(
                "name", "Spring WebSocket MCP Server",
                "version", "1.0.0"
        ));

        response.put("result", result);
        return response;
    }

    private Map<String, Object> handleListTools(String id) {
        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);

        List<Map<String, Object>> tools = Arrays.asList(
                Map.of(
                        "name", "spring_service_call",
                        "description", "Spring 서비스 메소드를 호출합니다",
                        "inputSchema", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "serviceName", Map.of("type", "string", "description", "서비스 이름"),
                                        "methodName", Map.of("type", "string", "description", "메소드 이름"),
                                        "parameters", Map.of("type", "object", "description", "메소드 파라미터")
                                ),
                                "required", Arrays.asList("serviceName", "methodName")
                        )
                ),
                Map.of(
                        "name", "api_call",
                        "description", "외부 API를 호출합니다",
                        "inputSchema", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "url", Map.of("type", "string", "description", "API URL"),
                                        "method", Map.of("type", "string", "description", "HTTP 메소드", "default", "GET"),
                                        "headers", Map.of("type", "object", "description", "HTTP 헤더"),
                                        "body", Map.of("type", "object", "description", "요청 본문")
                                ),
                                "required", Arrays.asList("url")
                        )
                )
        );

        response.put("result", Map.of("tools", tools));
        return response;
    }

    private Map<String, Object> handleCallTool(JsonNode request, String id) {
        JsonNode params = request.get("params");
        String toolName = params.get("name").asText();
        JsonNode arguments = params.get("arguments");

        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);

        try {
            String result = executeTool(toolName, arguments);
            response.put("result", Map.of(
                    "content", Arrays.asList(
                            Map.of("type", "text", "text", result)
                    ),
                    "isError", false
            ));
        } catch (Exception e) {
            response.put("result", Map.of(
                    "content", Arrays.asList(
                            Map.of("type", "text", "text", "오류: " + e.getMessage())
                    ),
                    "isError", true
            ));
        }

        return response;
    }

    private Map<String, Object> handleInitialized(String id) {
        // 초기화 완료 알림 처리
        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        if (id != null) {
            response.put("id", id);
        }
        response.put("result", Map.of("status", "initialized"));
        return response;
    }

    private String executeTool(String toolName, JsonNode arguments) {
        switch (toolName) {
            case "spring_service_call":
                return executeSpringService(arguments);
            case "api_call":
                return executeApiCall(arguments);
            default:
                throw new RuntimeException("Unknown tool: " + toolName);
        }
    }

    private String executeSpringService(JsonNode arguments) {
        String serviceName = arguments.get("serviceName").asText();
        String methodName = arguments.get("methodName").asText();
        JsonNode parameters = arguments.get("parameters");

        // 실제 Spring 서비스 호출 로직
        return String.format("서비스 %s의 %s 메소드 호출 완료. 파라미터: %s",
                serviceName, methodName, parameters.toString());
    }

    private String executeApiCall(JsonNode arguments) {
        String url = arguments.get("url").asText();
        String method = arguments.has("method") ? arguments.get("method").asText() : "GET";

        // 실제 API 호출 로직 (RestTemplate 또는 WebClient 사용)
        return String.format("%s 요청을 %s로 전송 완료", method, url);
    }

    private Map<String, Object> createErrorResponse(String id, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", Map.of(
                "code", -32000,
                "message", message
        ));
        return response;
    }

    private void sendErrorResponse(WebSocketSession session, String message) {
        try {
            Map<String, Object> errorResponse = createErrorResponse(null, message);
            String jsonResponse = objectMapper.writeValueAsString(errorResponse);
            session.sendMessage(new TextMessage(jsonResponse));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 모든 연결된 클라이언트에게 브로드캐스트
    public void broadcastToAll(String message) {
        sessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
