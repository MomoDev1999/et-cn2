package com.function;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

/**
 * Azure Functions with HTTP Trigger.
 */
public class Function {

    private final String BACKEND_URL;
    private final String EVENTGRID_ENDPOINT;
    private final String EVENTGRID_KEY;
    private final ObjectMapper objectMapper = new ObjectMapper();


    public Function() {
        this.BACKEND_URL = System.getenv("BACKEND_URL");
        this.EVENTGRID_ENDPOINT = System.getenv("EVENTGRID_ENDPOINT");
        this.EVENTGRID_KEY = System.getenv("EVENTGRID_KEY");
    }


    @FunctionName("HttpRegistrarEmpleado")
    public HttpResponseMessage run(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS)
                HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        context.getLogger().info("Java HTTP trigger processed a request.");

        // Validar que la URL del backend esté configurada
        if (BACKEND_URL == null || BACKEND_URL.isEmpty()) {
            context.getLogger().severe("La variable de entorno BACKEND_URL no está configurada.");
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error de configuración: la variable de entorno BACKEND_URL no está definida.").build();
        }

        // Validar que la URL y clave de EventGrid estén configuradas
        if (EVENTGRID_ENDPOINT == null || EVENTGRID_ENDPOINT.isEmpty() ||
            EVENTGRID_KEY == null || EVENTGRID_KEY.isEmpty()) {
            context.getLogger().severe("La configuración de EventGrid no está completa.");
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error de configuración: EventGrid no está correctamente configurado.").build();
        }

        // Obtener el body como JSON
        String requestBody = request.getBody().orElse("");
        if (requestBody.isEmpty()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body("No se recibieron datos para el empleado").build();
        }

        try {
            // Crear el cliente HTTP
            HttpClient client = HttpClient.newHttpClient();

            // Crear la petición POST al backend
            HttpRequest backendRequest = HttpRequest.newBuilder()
                .uri(URI.create(BACKEND_URL + "/api/register/employee"))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(requestBody))
                .build();

            // Enviar la petición y obtener la respuesta
            HttpResponse<String> backendResponse = client.send(backendRequest, BodyHandlers.ofString());

            // Si el empleado se creó exitosamente (201), enviar evento a EventGrid
            if (backendResponse.statusCode() == 200) {
                // Construir el evento EventGrid
                ObjectNode event = JsonNodeFactory.instance.objectNode();
                event.put("id", UUID.randomUUID().toString());
                event.put("eventType", "EmpleadoCreado");
                event.put("subject", "empleado/creado");
                event.put("eventTime", java.time.OffsetDateTime.now().toString());
                event.put("dataVersion", "1.0");
                // Puedes poner el body de respuesta del backend o el request original
                event.set("data", objectMapper.readTree(backendResponse.body()));

                String eventGridPayload = objectMapper.writeValueAsString(Collections.singletonList(event));

                // Crear la petición POST a EventGrid
                HttpRequest eventGridRequest = HttpRequest.newBuilder()
                    .uri(URI.create(EVENTGRID_ENDPOINT))
                    .header("Content-Type", "application/json")
                    .header("aeg-sas-key", EVENTGRID_KEY)
                    .POST(BodyPublishers.ofString(eventGridPayload))
                    .build();

                // Enviar el evento a EventGrid
                HttpResponse<String> eventGridResponse = client.send(eventGridRequest, BodyHandlers.ofString());

                if (eventGridResponse.statusCode() >= 200 && eventGridResponse.statusCode() < 300) {
                    context.getLogger().info("Evento enviado a EventGrid correctamente.");
                } else {
                    context.getLogger().severe("Error al enviar evento a EventGrid: " + eventGridResponse.body());
                }
            }

            // Retornar la respuesta del backend
            return request.createResponseBuilder(HttpStatus.valueOf(backendResponse.statusCode()))
                .body(backendResponse.body())
                .build();

        } catch (Exception e) {
            // Validar si la excepción es por backend no disponible
            Throwable cause = e.getCause();
            if (e instanceof ConnectException || (cause != null && cause instanceof ConnectException)) {
                context.getLogger().severe("Backend no disponible: " + e.getMessage());
                return request.createResponseBuilder(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("El servicio de backend no está disponible. Intente más tarde.").build();
            }
            if (e instanceof SocketTimeoutException || (cause != null && cause instanceof SocketTimeoutException)) {
                context.getLogger().severe("Timeout al conectar con backend: " + e.getMessage());
                return request.createResponseBuilder(HttpStatus.GATEWAY_TIMEOUT)
                    .body("Tiempo de espera agotado al conectar con el backend. Intente más tarde.").build();
            }
            context.getLogger().severe("Error al enviar datos al backend: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error al procesar la solicitud: " + e.getMessage()).build();
        }
    }
}
