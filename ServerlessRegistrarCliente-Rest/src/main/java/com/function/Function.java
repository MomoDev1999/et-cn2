package com.function;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

/*
 * Azure Function para procesar el registro de clientes.
 * Recibe datos de registro, valida y envía al backend con autenticación.
 */

/**
 * Azure Functions with HTTP Trigger.
 */
public class Function {
     // Componentes para comunicación HTTP y procesamiento JSON
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String backendUrl;
    private final String secretKey;
    private final String eventGridTopicEndpoint;
    private final String eventGridTopicKey;


    // Constructor: inicializa y valida configuración
    public Function() {
        this.backendUrl = System.getenv("BACKEND_URL");
        this.secretKey = System.getenv("SERVERLESS_SECRET_KEY");
        this.eventGridTopicEndpoint = System.getenv("EVENTGRID_ENDPOINT");
        this.eventGridTopicKey = System.getenv("EVENTGRID_KEY");

        if (this.backendUrl == null || this.backendUrl.trim().isEmpty()) {
            throw new IllegalStateException("BACKEND_URL no está configurada");
        }
        if (this.secretKey == null || this.secretKey.trim().isEmpty()) {
            throw new IllegalStateException("SERVERLESS_SECRET_KEY no está configurada");
        }
        if (this.eventGridTopicEndpoint == null || this.eventGridTopicEndpoint.trim().isEmpty()) {
            throw new IllegalStateException("EVENTGRID_ENDPOINT no está configurado");
        }
        if (this.eventGridTopicKey == null || this.eventGridTopicKey.trim().isEmpty()) {
            throw new IllegalStateException("EVENTGRID_KEY no está configurado");
        }

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Endpoint principal para registro de clientes
     * @param request Datos de registro del cliente
     * @param context Contexto de ejecución de Azure Functions
     */
    @FunctionName("RegistrarCliente")
    public HttpResponseMessage run(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "register/cliente")
                HttpRequestMessage<String> request,
            final ExecutionContext context) {
        
        context.getLogger().info("Iniciando proceso de registro de cliente");

        try {
            // 1. Validación de solicitud
            if (request == null) {
                context.getLogger().severe("La solicitud es nula");
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Solicitud inválida")
                    .build();
            }

            // 2. Validación de cuerpo de solicitud
            String requestBody = request.getBody();
            if (requestBody == null || requestBody.trim().isEmpty()) {
                context.getLogger().warning("Cuerpo de la solicitud vacío");
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("El cuerpo de la solicitud no puede estar vacío")
                    .build();
            }

            // 3. Validación de formato JSON y campos requeridos
            JsonNode jsonNode;
            String username, email;
            try {
                jsonNode = objectMapper.readTree(requestBody);
                
                // Validar campos requeridos
                if (!jsonNode.has("username") || !jsonNode.has("email") || !jsonNode.has("password")) {
                    return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .body("Faltan campos requeridos: username, email, password")
                        .build();
                }

                // Obtener valores para validación
                username = jsonNode.get("username").asText().trim();
                email = jsonNode.get("email").asText().trim();

                // Validar que los campos no estén vacíos
                if (username.isEmpty() || email.isEmpty() || 
                    jsonNode.get("password").asText().trim().isEmpty()) {
                    return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .body("Los campos username, email y password no pueden estar vacíos")
                        .build();
                }

            } catch (Exception e) {
                context.getLogger().warning("JSON inválido: " + e.getMessage());
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Formato JSON inválido")
                    .build();
            }

            // 4. Validar si el usuario ya existe
            try {
                String signature = generateServerlessSignature();
                
                // Consultar por username
                HttpRequest checkUsernameRequest = HttpRequest.newBuilder()
                    .uri(URI.create(backendUrl + "/api/users/check-username/" + username))
                    .header("serverlessSignature", signature)
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

                HttpResponse<String> usernameResponse = httpClient.send(
                    checkUsernameRequest,
                    HttpResponse.BodyHandlers.ofString()
                );

                if (usernameResponse.statusCode() == 200) {
                    return request.createResponseBuilder(HttpStatus.CONFLICT)
                        .body("El nombre de usuario ya está en uso")
                        .build();
                }

                // Consultar por email
                HttpRequest checkEmailRequest = HttpRequest.newBuilder()
                    .uri(URI.create(backendUrl + "/api/users/check-email/" + email))
                    .header("serverlessSignature", signature)
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

                HttpResponse<String> emailResponse = httpClient.send(
                    checkEmailRequest,
                    HttpResponse.BodyHandlers.ofString()
                );

                if (emailResponse.statusCode() == 200) {
                    return request.createResponseBuilder(HttpStatus.CONFLICT)
                        .body("El correo electrónico ya está registrado")
                        .build();
                }

            } catch (Exception e) {
                context.getLogger().severe("Error validando usuario existente: " + e.getMessage());
                return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error validando usuario")
                    .build();
            }

            // 5. Generación de firma de autenticación para registro
            String signature;
            try {
                signature = generateServerlessSignature();
            } catch (Exception e) {
                context.getLogger().severe("Error generando firma: " + e.getMessage());
                return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error en la autenticación")
                    .build();
            }

            // 6. Preparación de solicitud al backend
            HttpRequest backendRequest;
            try {
                backendRequest = HttpRequest.newBuilder()
                    .uri(URI.create(backendUrl + "/api/register/cliente"))
                    .header("Content-Type", "application/json")
                    .header("serverlessSignature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            } catch (Exception e) {
                context.getLogger().severe("Error preparando solicitud: " + e.getMessage());
                return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error preparando la solicitud al servidor")
                    .build();
            }

            // 7. Envío de solicitud al backend
            HttpResponse<String> backendResponse;
            try {
                backendResponse = httpClient.send(
                    backendRequest,
                    HttpResponse.BodyHandlers.ofString()
                );
            } catch (Exception e) {
                context.getLogger().severe("Error en la comunicación con el backend: " + e.getMessage());
                return request.createResponseBuilder(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Error de comunicación con el servidor")
                    .build();
            }

            // 8. Procesamiento de respuesta del backend
            int statusCode = backendResponse.statusCode();
            String responseBody = backendResponse.body();

            if (statusCode >= 500) {
                context.getLogger().severe("Error del servidor backend: " + responseBody);
                return request.createResponseBuilder(HttpStatus.BAD_GATEWAY)
                    .body("Error en el servidor de registro")
                    .build();
            }

            context.getLogger().info("Registro procesado con código de estado: " + statusCode);



             // Publicar evento en Event Grid si el registro fue exitoso
            if (statusCode == 200) {
                context.getLogger().info("Registro exitoso. Publicando evento en Event Grid");

                ObjectNode event = objectMapper.createObjectNode();
                event.put("id", UUID.randomUUID().toString());
                event.put("eventType", "ClienteRegistrado");
                event.put("subject", "usuarios/cliente/nuevo");
                event.put("eventTime", OffsetDateTime.now().toString());
                event.put("dataVersion", "1.0");
                JsonNode userData = objectMapper.readTree(requestBody);
                event.set("data", userData);

                String eventJson = objectMapper.writeValueAsString(List.of(event));

                HttpRequest eventRequest = HttpRequest.newBuilder()
                        .uri(URI.create(eventGridTopicEndpoint))
                        .header("aeg-sas-key", eventGridTopicKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(eventJson))
                        .build();

                HttpResponse<String> eventResponse = httpClient.send(
                        eventRequest, HttpResponse.BodyHandlers.ofString());

                context.getLogger().info("Event Grid status: " + eventResponse.statusCode());
            }


            return request.createResponseBuilder(HttpStatus.valueOf(statusCode))
                .body(responseBody)
                .build();

        } catch (Exception e) {
            context.getLogger().severe("Error no manejado: " + e.getMessage());
            e.printStackTrace();
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error interno del servidor")
                .build();
        }
    }

    /**
     * Genera firma HMAC-SHA256 para autenticación con el backend
     * @return Firma en formato timestamp:token
     */
    private String generateServerlessSignature() {
        try {
            long timestamp = System.currentTimeMillis() / 1000;
            String data = timestamp + ":" + secretKey;
            
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                secretKey.getBytes(StandardCharsets.UTF_8), 
                "HmacSHA256"
            );
            sha256_HMAC.init(secretKeySpec);

            byte[] hash = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
            String token = Base64.getEncoder().encodeToString(hash);

            return timestamp + ":" + token;
        } catch (Exception e) {
            throw new RuntimeException("Error generando la firma: " + e.getMessage(), e);
        }
    }
}
