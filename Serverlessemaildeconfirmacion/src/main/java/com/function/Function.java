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
 * Función Azure para enviar mensajes de confirmación cuando se crea un nuevo usuario
 */
public class Function {
    private final Gson gson = new Gson();

    @FunctionName("UserCreationNotification")
    public HttpResponseMessage run(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS)
                HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        context.getLogger().info("Procesando notificación de creación de usuario...");

        try {
            // Obtener y validar el cuerpo de la petición
            String requestBody = request.getBody().orElse("");
            context.getLogger().info("Cuerpo de la petición recibido: " + requestBody);

            if (requestBody.isEmpty()) {
                context.getLogger().warning("Cuerpo de la petición vacío");
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Por favor proporcione la información del usuario creado")
                    .build();
            }

            try {
                // Parsear el JSON y mostrar su contenido
                JsonObject userData = gson.fromJson(requestBody, JsonObject.class);
                context.getLogger().info("JSON parseado correctamente: " + userData.toString());
                
                // Extraer información del usuario
                String username = userData.has("username") ? userData.get("username").getAsString() : "No username";
                String email = userData.has("email") ? userData.get("email").getAsString() : "No email";
                String role = userData.has("role") ? userData.get("role").getAsString() : "No role";

                // Crear mensaje de confirmación
                String confirmationMessage = String.format(
                    "¡Bienvenido a nuestro sistema!\n" +
                    "--------------------------------\n" +
                    "Usuario: %s\n" +
                    "Email: %s\n" +
                    "Rol: %s\n" +
                    "--------------------------------\n" +
                    "Fecha de registro: %s\n\n" +
                    "Su cuenta ha sido creada exitosamente.\n" +
                    "Por favor, conserve esta información para futuros accesos.",
                    username,
                    email,
                    role,
                    java.time.LocalDateTime.now()
                );

                // Registrar la información
                context.getLogger().info("Mensaje de confirmación generado para el usuario:");
                context.getLogger().info("- Username: " + username);
                context.getLogger().info("- Email: " + email);
                context.getLogger().info("- Rol: " + role);

                // Aquí se podrían agregar acciones adicionales como:
                // - Enviar email real al usuario
                // - Almacenar el registro en una base de datos
                // - Notificar a administradores
                // - etc.

                return request.createResponseBuilder(HttpStatus.OK)
                    .body(confirmationMessage)
                    .build();

            } catch (Exception jsonError) {
                context.getLogger().severe("Error al procesar JSON: " + jsonError.getMessage());
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Error al procesar los datos del usuario: " + jsonError.getMessage())
                    .build();
            }

        } catch (Exception e) {
            context.getLogger().severe("Error general: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error al procesar la notificación: " + e.getMessage())
                .build();
        }
    }
}
