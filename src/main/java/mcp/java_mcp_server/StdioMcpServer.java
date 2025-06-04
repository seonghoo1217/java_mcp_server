package mcp.java_mcp_server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Component
public class StdioMcpServer implements CommandLineRunner {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Scanner scanner = new Scanner(System.in);

    @Override
    public void run(String... args) throws Exception {
        startMcpServer();
    }

    public void startMcpServer() {
        try {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.trim().isEmpty()) continue;

                // JSON-RPC 요청 파싱
                JsonNode request = objectMapper.readTree(line);

                // 응답 처리
                CompletableFuture.supplyAsync(() -> processRequest(request))
                        .thenAccept(this::sendResponse);
            }
        } catch (Exception e) {
            sendErrorResponse("서버에서 문제 발생: " + e.getMessage());
        }
    }

    private Map<String, Object> processRequest(JsonNode request) {
        String method = request.get("method").asText();
        String id = request.has("id") ? request.get("id").asText() : null;

        try {
            switch (method) {
                case "initialize":
                    return handleInitialize(request, id);
                case "tools/list":
                    return handleListTools(id);
                case "tools/call":
                    return handleCallTool(request, id);
                default:
                    return createErrorResponse(id, "정의되지 않은 메서드: " + method);
            }
        } catch (Exception e) {
            return createErrorResponse(id, e.getMessage());
        }
    }

    private Map<String, Object> handleInitialize(JsonNode request, String id) {
        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);

        Map<String, Object> result = new HashMap<>();
        result.put("protocolVersion", "2024-11-05");
        result.put("capabilities", Map.of(
                "tools", Map.of("listChanged", true)
        ));
        result.put("serverInfo", Map.of(
                "name", "Spring MCP Server",
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
                        "name", "get_user_info",
                        "description", "사용자 정보를 조회합니다",
                        "inputSchema", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "userId", Map.of("type", "string", "description", "사용자 ID")
                                ),
                                "required", Arrays.asList("userId")
                        )
                ),
                Map.of(
                        "name", "database_query",
                        "description", "데이터베이스 쿼리를 실행합니다",
                        "inputSchema", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "query", Map.of("type", "string", "description", "SQL 쿼리"),
                                        "limit", Map.of("type", "number", "description", "결과 제한", "default", 10)
                                ),
                                "required", Arrays.asList("query")
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

    private String executeTool(String toolName, JsonNode arguments) {
        switch (toolName) {
            case "get_user_info":
                String userId = arguments.get("userId").asText();
                return getUserInfo(userId);
            case "database_query":
                String query = arguments.get("query").asText();
                int limit = arguments.has("limit") ? arguments.get("limit").asInt() : 10;
                return executeQuery(query, limit);
            default:
                throw new RuntimeException("Unknown tool: " + toolName);
        }
    }

    private String getUserInfo(String userId) {
        // 실제 사용자 정보 조회 로직
        return "사용자 ID " + userId + "의 정보: 이름=홍길동, 이메일=hong@example.com";
    }

    private String executeQuery(String query, int limit) {
        // 실제 데이터베이스 쿼리 실행 로직
        return "쿼리 실행 결과: " + query + " (최대 " + limit + "개 결과)";
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

    private void sendResponse(Map<String, Object> response) {
        try {
            String jsonResponse = objectMapper.writeValueAsString(response);
            System.out.println(jsonResponse);
            System.out.flush();
        } catch (Exception e) {
            sendErrorResponse("응답 직렬화 오류: " + e.getMessage());
        }
    }

    private void sendErrorResponse(String message) {
        try {
            Map<String, Object> errorResponse = createErrorResponse(null, message);
            String jsonResponse = objectMapper.writeValueAsString(errorResponse);
            System.err.println(jsonResponse);
            System.err.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
