package com.function;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

/**
 * Azure Function para consultar alertas mediante GraphQL
 */
public class Function {
    // Variables de configuración
    private String BACKEND_URL;
    private String SERVERLESS_SECRET_KEY;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Constructor que inicializa las variables de entorno
    public Function() {
        this.BACKEND_URL = System.getenv("BACKEND_URL");
        this.SERVERLESS_SECRET_KEY = System.getenv("SERVERLESS_SECRET_KEY");
    }

    /**
     * Función principal que procesa las peticiones HTTP
     * @param request Petición HTTP recibida
     * @param context Contexto de ejecución
     * @return Respuesta HTTP con los resultados
     */
    @FunctionName("HttpExample")
    public HttpResponseMessage run(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET, HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS)
                HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        context.getLogger().info("Java HTTP trigger processed a request.");

        // Buscar el header de autorización sin importar mayúsculas/minúsculas
        String authHeader = null;
        for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
            if (entry.getKey().equalsIgnoreCase("authorization")) {
                authHeader = entry.getValue();
                break;
            }
        }

        context.getLogger().info("Authorization header completo: " + authHeader);

        if (authHeader == null) {
            context.getLogger().warning("No se encontró el header de autorización");
            return request.createResponseBuilder(HttpStatus.UNAUTHORIZED)
                .body("{\"error\": \"Se requiere token JWT de autenticación\"}")
                .build();
        }

        if (!authHeader.startsWith("Bearer ")) {
            context.getLogger().warning("Formato de token inválido. Debe comenzar con 'Bearer '");
            return request.createResponseBuilder(HttpStatus.UNAUTHORIZED)
                .body("{\"error\": \"Formato de token inválido. Debe comenzar con 'Bearer '\"}")
                .build();
        }

        // Extraer el token JWT del header
        String token = authHeader.substring(7).trim(); // Remover "Bearer " del inicio y espacios en blanco
        context.getLogger().info("Token extraído: " + token);

        if (token.isEmpty()) {
            context.getLogger().warning("Token vacío después de extraer 'Bearer '");
            return request.createResponseBuilder(HttpStatus.UNAUTHORIZED)
                .body("{\"error\": \"Token JWT vacío\"}")
                .build();
        }

        // Validar formato básico del token JWT
        if (!token.matches("^[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_.+/=]+$")) {
            context.getLogger().warning("Formato de token JWT inválido");
            return request.createResponseBuilder(HttpStatus.UNAUTHORIZED)
                .body("{\"error\": \"Formato de token JWT inválido\"}")
                .build();
        }

        // Construir consulta GraphQL
        Map<String, String> graphqlQuery = new HashMap<>();
        graphqlQuery.put("query", """
            query {
                alerts {
                    id
                    message
                    userEmail
                    userRole
                    modificationType
                    createdAt
                    read
                }
            }
        """);
        
        // Convertir consulta a JSON
        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(graphqlQuery);
        } catch (JsonProcessingException e) {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error al procesar la consulta GraphQL: " + e.getMessage())
                .build();
        }

        // Generar firma para autenticación
        String signature = generateSignature(jsonBody);

        // Validar configuración
        if (BACKEND_URL == null || BACKEND_URL.trim().isEmpty()) {
            context.getLogger().severe("BACKEND_URL no está configurada");
            throw new RuntimeException("BACKEND_URL no está configurada en local.settings.json");
        }
        if (SERVERLESS_SECRET_KEY == null || SERVERLESS_SECRET_KEY.trim().isEmpty()) {
            context.getLogger().severe("SERVERLESS_SECRET_KEY no está configurada");
            throw new RuntimeException("SERVERLESS_SECRET_KEY no está configurada en local.settings.json");
        }

        // Asegurar que la URL termine con /
        if (!BACKEND_URL.endsWith("/")) {
            BACKEND_URL = BACKEND_URL + "/";
        }

        String fullUrl = BACKEND_URL + "api/alerts";
        context.getLogger().info("URL completa del backend: " + fullUrl);

        // Preparar petición HTTP al backend
        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(fullUrl))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + token)
            .header("serverlessSignature", signature)
            .GET()
            .build();

        context.getLogger().info("Enviando petición GET a: " + fullUrl);
        context.getLogger().info("Headers de la petición: " + httpRequest.headers().map());

        // Enviar petición y procesar respuesta
        HttpResponse<String> response = null;
        try {
            response = java.net.http.HttpClient.newHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());
            context.getLogger().info("Código de respuesta: " + response.statusCode());
            context.getLogger().info("Respuesta del backend: " + response.body());
        } catch (Exception e) {
            context.getLogger().severe("Error al enviar la petición: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error al enviar la petición: " + e.getMessage())
                .build();
        }

        // Procesar respuesta del backend
        if (response != null && response.body() != null) {
            if (response.statusCode() == 200) {
                Map<String, Object> graphqlResponse = new HashMap<>();
                try {
                    // Convertir respuesta a formato GraphQL
                    graphqlResponse.put("data", objectMapper.readTree(response.body()));
                    return request.createResponseBuilder(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body(objectMapper.writeValueAsString(graphqlResponse))
                        .build();
                } catch (JsonProcessingException e) {
                    return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Error al procesar la respuesta: " + e.getMessage())
                        .build();
                }
            } else {
                return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Respuesta vacía del backend")
                    .build();
            }
        } else {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Respuesta vacía del backend")
                .build();
        }
    }

    /**
     * Genera una firma HMAC-SHA384 para autenticar la petición
     * @param data Datos a firmar
     * @return Firma generada en Base64
     */
    private String generateSignature(String data) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA384");
            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(
                SERVERLESS_SECRET_KEY.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "HmacSHA384"
            );
            mac.init(secretKey);
            byte[] signatureBytes = mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(signatureBytes);
        } catch (Exception e) {
            throw new RuntimeException("Error al generar la firma: " + e.getMessage());
        }
    }
}
