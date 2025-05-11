package com.function;

import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

/**
 * Función Azure para manejar notificaciones de alertas cuando se actualizan datos de usuarios
 */
public class Function {
    // Instancia de Gson para procesar JSON
    private final Gson gson = new Gson();

    /**
     * Función que se activa mediante una petición HTTP POST
     * Procesa las alertas enviadas desde el backend cuando se actualizan datos de usuarios
     */
    @FunctionName("AlertNotification")
    public HttpResponseMessage run(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS)
                HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        context.getLogger().info("Iniciando procesamiento de alerta...");

        try {
            // Obtener y validar el cuerpo de la petición
            String requestBody = request.getBody().orElse("");
            context.getLogger().info("Cuerpo de la petición recibido: " + requestBody);

            if (requestBody.isEmpty()) {
                context.getLogger().warning("Cuerpo de la petición vacío");
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Por favor proporcione un cuerpo de petición con información de alerta")
                    .build();
            }

            try {
                // Intentar parsear el JSON y mostrar su contenido
                JsonObject alertData = gson.fromJson(requestBody, JsonObject.class);
                context.getLogger().info("JSON parseado correctamente: " + alertData.toString());
                
                // Extraer y validar la información requerida
                String message = alertData.has("message") ? alertData.get("message").getAsString() : "No message";
                String userEmail = alertData.has("userEmail") ? alertData.get("userEmail").getAsString() : "No email";
                String modificationType = alertData.has("modificationType") ? alertData.get("modificationType").getAsString() : "No type";
                String userRole = alertData.has("userRole") ? alertData.get("userRole").getAsString() : "No role";

                // Crear respuesta formateada
                String responseMessage = String.format(
                    "Alerta procesada exitosamente:\n" +
                    "--------------------------------\n" +
                    "Mensaje: %s\n" +
                    "Usuario: %s\n" +
                    "Tipo de modificación: %s\n" +
                    "Rol del usuario: %s\n" +
                    "--------------------------------\n" +
                    "Estado: Procesado correctamente\n" +
                    "Fecha y hora: %s",
                    message,
                    userEmail,
                    modificationType,
                    userRole,
                    java.time.LocalDateTime.now()
                );

                // Registrar la información detallada
                context.getLogger().info("Datos procesados de la alerta:");
                context.getLogger().info(responseMessage);

                return request.createResponseBuilder(HttpStatus.OK)
                    .body(responseMessage)
                    .build();

            } catch (Exception jsonError) {
                context.getLogger().severe("Error al parsear JSON: " + jsonError.getMessage());
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Error al procesar el JSON: " + jsonError.getMessage())
                    .build();
            }

        } catch (Exception e) {
            context.getLogger().severe("Error general: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error al procesar la notificación de alerta: " + e.getMessage())
                .build();
        }
    }
}
