// Paquete que contiene el controlador de autenticación
package com.example.Backend.Controller;

// Importaciones de Spring Framework necesarias para el controlador
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;

// Importaciones de DTOs y servicios de la aplicación
import com.example.Backend.DTO.JwtResponseDto;
import com.example.Backend.DTO.LoginDto;
import com.example.Backend.DTO.RegisterDto;
import com.example.Backend.DTO.UserDTO;
import com.example.Backend.DTO.AlertDTO;
import com.example.Backend.DTO.UserConfirmationDTO;
import com.example.Backend.Security.JwtGenerator;
import com.example.Backend.Service.RolService;
import com.example.Backend.Service.UserService;

import java.util.List;

// Controlador REST que maneja la autenticación y gestión de usuarios
@RestController
@RequestMapping("/api")
public class AuthController {

    // Inyección del servicio de usuarios
    @Autowired
    private UserService userService;
    
    // Inyección del servicio de roles
    @Autowired
    private RolService rolService;
    
    // Inyección del generador de tokens JWT
    @Autowired
    private JwtGenerator jwtGenerator;

    @Autowired
    private ObjectMapper objectMapper;

    // Inyectar la clave secreta desde la configuración
    @Value("${serverless.secret.key}")
    private String serverlessSecretKey;

    // Endpoint para autenticar usuarios y generar token JWT
    @PostMapping("/login")
    public ResponseEntity<JwtResponseDto> login(@RequestBody LoginDto loginDto) {
        return ResponseEntity.ok(userService.login(loginDto));
    }

    // Endpoint para recibir datos procesados desde serverless
    @PostMapping("/register/cliente")
    public ResponseEntity<UserConfirmationDTO> register(
            @RequestBody RegisterDto registerDto,
            @RequestHeader(required = true) String serverlessSignature) {
        try {
            // Verificar que la petición viene de la función serverless
            if (!isValidServerlessRequest(serverlessSignature)) {
                return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
            }

            // Procesar el registro
            UserConfirmationDTO confirmation = userService.registerWithConfirmation(registerDto);
            
            if (confirmation != null) {
                return new ResponseEntity<>(confirmation, HttpStatus.CREATED);
            } else {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
            
        } catch (Exception e) {
            System.err.println("Error en el proceso de registro: " + e.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Método para validar que la petición viene de la función serverless
    private boolean isValidServerlessRequest(String signature) {
        try {
            if (signature == null || signature.trim().isEmpty()) {
                return false;
            }

            // Usar la clave secreta inyectada desde la configuración
            if (serverlessSecretKey == null || serverlessSecretKey.trim().isEmpty()) {
                System.err.println("Error: serverless.secret.key no está configurada");
                return false;
            }

            // Dividir la firma en sus componentes (timestamp:token)
            String[] signatureParts = signature.split(":");
            if (signatureParts.length != 2) {
                return false;
            }

            // Validar el timestamp y el token
            long timestamp = Long.parseLong(signatureParts[0]);
            String receivedToken = signatureParts[1];

            // Verificar que la firma no haya expirado (5 minutos)
            long currentTime = System.currentTimeMillis() / 1000;
            if (currentTime - timestamp > 300) {
                System.err.println("Error: Firma expirada");
                return false;
            }

            return true;
        } catch (Exception e) {
            System.err.println("Error validando firma serverless: " + e.getMessage());
            return false;
        }
    }

    // Endpoint para registrar nuevos empleados con confirmación
    @PostMapping("/register/employee")
    public ResponseEntity<UserConfirmationDTO> registerEmployee(@RequestBody RegisterDto registerDto) {
        UserConfirmationDTO confirmation = userService.registerEmployeeWithConfirmation(registerDto);
        return new ResponseEntity<>(confirmation, HttpStatus.CREATED);
    }

    // Endpoint para renovar el token JWT
    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToke(Authentication authentication){
        String token = jwtGenerator.refreshToken(authentication);
        JwtResponseDto jwtRefresh = new JwtResponseDto(token);
        return new ResponseEntity<JwtResponseDto>(jwtRefresh, HttpStatus.OK);
    }

    // Endpoint para obtener información del usuario autenticado
    @GetMapping("/logued")
    public ResponseEntity<UserDTO> getLoguedUser(@RequestHeader HttpHeaders headers){
        return new ResponseEntity<>(userService.getLoguedUser(headers), HttpStatus.OK);
    }
    
    // Endpoint para actualizar información de cliente y generar alerta
    @PutMapping("/update/client")
    public ResponseEntity<AlertDTO> updateClient(@RequestBody UserDTO userDTO) {
        AlertDTO alert = userService.updateClient(userDTO);
        return new ResponseEntity<>(alert, alert != null ? HttpStatus.OK : HttpStatus.NO_CONTENT);
    }

    // Endpoint para actualizar información de empleado y generar alerta
    @PutMapping("/update/employee")
    public ResponseEntity<AlertDTO> updateEmployee(@RequestBody UserDTO userDTO) {
        AlertDTO alert = userService.updateEmployee(userDTO);
        return new ResponseEntity<>(alert, alert != null ? HttpStatus.OK : HttpStatus.NO_CONTENT);
    }

    // Endpoint para obtener alertas de un usuario específico
    @GetMapping("/alerts/user/{userId}")
    public ResponseEntity<List<AlertDTO>> getUserAlerts(@PathVariable Long userId) {
        List<AlertDTO> alerts = userService.getUserAlerts(userId);
        return new ResponseEntity<>(alerts, HttpStatus.OK);
    }

    // Endpoint para obtener todas las alertas del sistema
    @GetMapping("/alerts")
    public ResponseEntity<List<AlertDTO>> getAllAlerts() {
        List<AlertDTO> alerts = userService.getAllAlerts();
        return new ResponseEntity<>(alerts, HttpStatus.OK);
    }

    // Endpoint para marcar una alerta como leída
    @PutMapping("/alerts/{alertId}/read")
    public ResponseEntity<Void> markAlertAsRead(@PathVariable Long alertId) {
        userService.markAlertAsRead(alertId);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    // Endpoint para verificar si un username ya existe
    @GetMapping("/users/check-username/{username}")
    public ResponseEntity<Boolean> checkUsernameExists(
            @PathVariable String username,
            @RequestHeader(required = true) String serverlessSignature) {
        try {
            if (!isValidServerlessRequest(serverlessSignature)) {
                return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
            }
            boolean exists = userService.existsByUsername(username);
            return new ResponseEntity<>(exists, exists ? HttpStatus.OK : HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            System.err.println("Error verificando username: " + e.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Endpoint para verificar si un email ya existe
    @GetMapping("/users/check-email/{email}")
    public ResponseEntity<Boolean> checkEmailExists(
            @PathVariable String email,
            @RequestHeader(required = true) String serverlessSignature) {
        try {
            if (!isValidServerlessRequest(serverlessSignature)) {
                return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
            }
            boolean exists = userService.existsByEmail(email);
            return new ResponseEntity<>(exists, exists ? HttpStatus.OK : HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            System.err.println("Error verificando email: " + e.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Endpoint para eliminar un cliente
    @DeleteMapping("/users/client/{id}")
    public ResponseEntity<Void> deleteClient(@PathVariable Long id) {
        try {
            userService.deleteUser(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Endpoint para eliminar un empleado
    @DeleteMapping("/users/employee/{id}")
    public ResponseEntity<Void> deleteEmployee(@PathVariable Long id) {
        try {
            userService.deleteUser(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Endpoint para obtener todos los usuarios
    @GetMapping("/users")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        List<UserDTO> users = userService.getAllUsers();
        return new ResponseEntity<>(users, HttpStatus.OK);
    }

    // Endpoint para obtener un usuario por ID
    @GetMapping("/users/{id}")
    public ResponseEntity<UserDTO> getUserById(@PathVariable Long id) {
        try {
            UserDTO user = userService.getUserById(id);
            return new ResponseEntity<>(user, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    // Endpoint para actualizar un usuario
    @PutMapping("/users/{id}")
    public ResponseEntity<UserDTO> updateUser(@PathVariable Long id, @RequestBody UserDTO userDTO) {
        try {
            UserDTO updatedUser = userService.updateUser(userDTO);
            return new ResponseEntity<>(updatedUser, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
