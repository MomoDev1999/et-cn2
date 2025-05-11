package com.example.Backend.Service.Imple;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.Backend.DTO.AlertDTO;
import com.example.Backend.DTO.JwtResponseDto;
import com.example.Backend.DTO.LoginDto;
import com.example.Backend.DTO.RegisterDto;
import com.example.Backend.DTO.UserConfirmationDTO;
import com.example.Backend.DTO.UserDTO;
import com.example.Backend.Exceptions.ConflictException;
import com.example.Backend.Exceptions.JwtAuthenticationException;
import com.example.Backend.Exceptions.NotFoundException;
import com.example.Backend.Model.Alert;
import com.example.Backend.Model.Rol;
import com.example.Backend.Model.UserEntity;
import com.example.Backend.Repository.AlertRepository;
import com.example.Backend.Repository.UserRepository;
import com.example.Backend.Security.JwtGenerator;
import com.example.Backend.Service.RolService;
import com.example.Backend.Service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;

// Clase principal que implementa la lógica de negocio para usuarios
@Service
public class UserServiceImple implements UserService {
    
    // Inyección de dependencias necesarias para el servicio
    @Autowired
    private UserRepository userRepository;        // Para operaciones CRUD de usuarios
    @Autowired
    private RolService rolService;               // Maneja los roles de usuarios
    @Autowired
    private AuthenticationManager authenticationManager;  // Maneja autenticación
    @Autowired
    private JwtGenerator jwtGenerator;           // Genera tokens JWT
    @Autowired
    private PasswordEncoder passwordEncoder;      // Encripta contraseñas
    @Autowired
    private AlertRepository alertRepository;      // Para operaciones CRUD de alertas
    @Autowired
    private RestTemplate restTemplate;           // Para hacer llamadas HTTP
    @Autowired
    private ObjectMapper objectMapper;           // Convierte objetos a/desde JSON

    // URLs configuradas para las funciones Azure
    @Value("${azure.functions.alert-url}")
    private String alertFunctionUrl;             // URL para enviar alertas
    @Value("${azure.functions.confirmation-url}")
    private String confirmationFunctionUrl;      // URL para enviar confirmaciones

    // Método para registrar un nuevo usuario cliente
    @Override
    public UserDTO register(RegisterDto registerDto) {
        // Verifica si el email ya existe
        if (userRepository.existsByEmail(registerDto.getEmail())){
            throw new ConflictException("El usuario existe!");
        }
        // Crea y configura el nuevo usuario
        UserEntity user = new UserEntity();
        user.setUsername(registerDto.getUsername());
        user.setPassword(passwordEncoder.encode(registerDto.getPassword()));
        user.setEmail(registerDto.getEmail());

        Rol rol = rolService.getRolname("USER").orElseThrow(()-> new NotFoundException("Rol no encontrado!"));
        Set<Rol> roles = new HashSet<>();
        roles.add(rol);
        user.setRoles(roles);
        userRepository.save(user);

        UserDTO userDto = new UserDTO();
        userDto.setUsername(user.getUsername());
        userDto.setPassword(user.getPassword());
        userDto.setEmail(user.getEmail());
        userDto.setRoles(user.getRoles());
        return userDto;
    }

    // Método para registrar un nuevo empleado
    @Override
    public UserDTO registerEmployee(RegisterDto registerDto) {
        // Similar al registro de cliente pero con rol EMPLOYEE
        if (userRepository.existsByEmail(registerDto.getEmail())){
            throw new ConflictException("El usuario existe!");
        }
        UserEntity user = new UserEntity();
        user.setUsername(registerDto.getUsername());
        user.setPassword(passwordEncoder.encode(registerDto.getPassword()));
        user.setEmail(registerDto.getEmail());

        Rol rol = rolService.getRolname("EMPLOYEE").orElseThrow(()-> new NotFoundException("Rol no encontrado!"));
        Set<Rol> roles = new HashSet<>();
        roles.add(rol);
        user.setRoles(roles);
        userRepository.save(user);

        UserDTO userDto = new UserDTO();
        userDto.setUsername(user.getUsername());
        userDto.setPassword(user.getPassword());
        userDto.setEmail(user.getEmail());
        userDto.setRoles(user.getRoles());
        return userDto;
    }

    // Método para autenticar usuarios y generar token JWT
    @Override
    public JwtResponseDto login(LoginDto loginDto) {
        // Intenta autenticar al usuario
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginDto.getEmail(),
                            loginDto.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            String token = jwtGenerator.generateToken(authentication);
            return new JwtResponseDto(token);
        } catch (AuthenticationException e) {
            throw new JwtAuthenticationException("Credenciales inválidas");
        }
    }

    @Override
    public UserDTO getLoguedUser(HttpHeaders headers) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        String email = ((UserEntity) authentication.getPrincipal()).getUsername();

        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(()-> new NotFoundException("Usuario no encontrado"));
        UserDTO userDto = new UserDTO();
        userDto.setEmail(user.getEmail());
        userDto.setUsername(user.getUsername());
        userDto.setRoles(user.getRoles());
        return userDto;
    }

    @Override
    public List<UserDTO> getAllUsers() {
        List<UserEntity> users = userRepository.findAll();
        return users.stream()
            .map(user -> {
                UserDTO userDto = new UserDTO();
                userDto.setEmail(user.getEmail());
                userDto.setUsername(user.getUsername());
                userDto.setRoles(user.getRoles());
                return userDto;
            })
            .collect(Collectors.toList());
    }

    @Override
    public UserDTO getUserById(Long id) {
        UserEntity user = userRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Usuario no encontrado con ID: " + id));
        
        UserDTO userDto = new UserDTO();
        userDto.setEmail(user.getEmail());
        userDto.setUsername(user.getUsername());
        userDto.setRoles(user.getRoles());
        return userDto;
    }

    @Override
    public UserDTO updateUser(UserDTO userDTO) {
        UserEntity user = userRepository.findByEmail(userDTO.getEmail())
            .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        
        user.setUsername(userDTO.getUsername());
        if (userDTO.getPassword() != null && !userDTO.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        }
        
        if (userDTO.getRoles() != null && !userDTO.getRoles().isEmpty()) {
            user.setRoles(userDTO.getRoles());
        }
        
        UserEntity updatedUser = userRepository.save(user);
        
        UserDTO updatedUserDto = new UserDTO();
        updatedUserDto.setEmail(updatedUser.getEmail());
        updatedUserDto.setUsername(updatedUser.getUsername());
        updatedUserDto.setRoles(updatedUser.getRoles());
        return updatedUserDto;
    }

    @Override
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new NotFoundException("Usuario no encontrado con ID: " + id);
        }
        userRepository.deleteById(id);
    }

    // Método privado para enviar alertas al trigger de Azure
    private void sendAlertToFunction(AlertDTO alertDTO) {
        try {
            // Crea el JSON para la alerta
            String jsonBody = String.format(
                "{\"message\":\"%s\",\"userEmail\":\"%s\",\"modificationType\":\"%s\",\"userRole\":\"%s\"}",
                alertDTO.getMessage(),
                alertDTO.getUserEmail(),
                alertDTO.getModificationType(),
                alertDTO.getUserRole()
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Usar directamente el String JSON como cuerpo
            HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);
            
            System.out.println("Enviando alerta al trigger:");
            System.out.println("URL: " + alertFunctionUrl);
            System.out.println("Datos: " + jsonBody);

            ResponseEntity<String> response = restTemplate.postForEntity(
                alertFunctionUrl,
                request,
                String.class
            );
            
            System.out.println("Respuesta del trigger: " + response.getStatusCode());

        } catch (Exception e) {
            System.err.println("Error al enviar alerta: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Método para actualizar datos de un cliente y generar alerta
    @Override
    public AlertDTO updateClient(UserDTO userDTO) {
        System.out.println("Intentando actualizar cliente: " + userDTO.getEmail());
        
        UserEntity user = userRepository.findByEmail(userDTO.getEmail())
            .orElseThrow(() -> new NotFoundException("Cliente no encontrado"));
        
        System.out.println("Roles del usuario: " + user.getRoles().stream()
            .map(Rol::getName)
            .collect(Collectors.joining(", ")));

        if (!user.getRoles().stream().anyMatch(rol -> rol.getName().equals("USER"))) {
            throw new ConflictException("El usuario no es un cliente");
        }

        boolean hasChanges = false;
        StringBuilder changes = new StringBuilder();

        if (!user.getUsername().equals(userDTO.getUsername())) {
            changes.append("Nombre de usuario actualizado de '")
                  .append(user.getUsername())
                  .append("' a '")
                  .append(userDTO.getUsername())
                  .append("'. ");
            user.setUsername(userDTO.getUsername());
            hasChanges = true;
        }

        if (userDTO.getPassword() != null && !userDTO.getPassword().isEmpty()) {
            changes.append("Contraseña actualizada. ");
            user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
            hasChanges = true;
        }

        if (hasChanges) {
            userRepository.save(user);
            
            Alert alert = new Alert();
            alert.setUser(user);
            alert.setMessage(changes.toString().trim());
            alert.setModificationType("UPDATE_CLIENT");
            alert.setCreatedAt(LocalDateTime.now());
            alert.setRead(false);
            
            Alert savedAlert = alertRepository.save(alert);
            AlertDTO alertDTO = convertToAlertDTO(savedAlert);
            
            sendAlertToFunction(alertDTO);
            
            return alertDTO;
        }

        return null;
    }

    @Override
    public AlertDTO updateEmployee(UserDTO userDTO) {
        UserEntity user = userRepository.findByEmail(userDTO.getEmail())
            .orElseThrow(() -> new NotFoundException("Empleado no encontrado"));
        
        if (!user.getRoles().stream().anyMatch(rol -> rol.getName().equals("EMPLOYEE"))) {
            throw new ConflictException("El usuario no es un empleado");
        }

        boolean hasChanges = false;
        StringBuilder changes = new StringBuilder();

        if (!user.getUsername().equals(userDTO.getUsername())) {
            changes.append("Nombre de usuario actualizado. ");
            user.setUsername(userDTO.getUsername());
            hasChanges = true;
        }

        if (userDTO.getPassword() != null && !userDTO.getPassword().isEmpty()) {
            changes.append("Contraseña actualizada. ");
            user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
            hasChanges = true;
        }

        if (hasChanges) {
            userRepository.save(user);
            
            Alert alert = new Alert();
            alert.setUser(user);
            alert.setMessage("Actualización de empleado: " + changes.toString());
            alert.setModificationType("UPDATE_EMPLOYEE");
            alert.setCreatedAt(LocalDateTime.now());
            alert.setRead(false);
            
            Alert savedAlert = alertRepository.save(alert);
            AlertDTO alertDTO = convertToAlertDTO(savedAlert);
            
            sendAlertToFunction(alertDTO);
            
            return alertDTO;
        }

        return null;
    }

    @Override
    public List<AlertDTO> getUserAlerts(Long userId) {
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        
        List<Alert> alerts = alertRepository.findByUserOrderByCreatedAtDesc(user);
        return alerts.stream()
            .map(this::convertToAlertDTO)
            .collect(Collectors.toList());
    }

    @Override
    public List<AlertDTO> getAllAlerts() {
        List<Alert> alerts = alertRepository.findAllByOrderByCreatedAtDesc();
        return alerts.stream()
            .map(this::convertToAlertDTO)
            .collect(Collectors.toList());
    }

    @Override
    public void markAlertAsRead(Long alertId) {
        Alert alert = alertRepository.findById(alertId)
            .orElseThrow(() -> new NotFoundException("Alerta no encontrada"));
        alert.setRead(true);
        alertRepository.save(alert);
    }

    // Método para convertir Alert a AlertDTO
    private AlertDTO convertToAlertDTO(Alert alert) {
        // Convierte una entidad Alert a su DTO correspondiente
        AlertDTO alertDTO = new AlertDTO();
        alertDTO.setId(alert.getId());
        alertDTO.setMessage(alert.getMessage());
        alertDTO.setUserEmail(alert.getUser().getEmail());
        alertDTO.setUserRole(alert.getUser().getRoles().stream()
            .map(Rol::getName)
            .collect(Collectors.joining(", ")));
        alertDTO.setModificationType(alert.getModificationType());
        alertDTO.setCreatedAt(alert.getCreatedAt());
        alertDTO.setRead(alert.isRead());
        return alertDTO;
    }

    // Método para registrar cliente con mensaje de confirmación
    @Override
    public UserConfirmationDTO registerWithConfirmation(RegisterDto registerDto) {
        // Registra el usuario y envía confirmación
        UserDTO userDTO = register(registerDto);
        
        // Prepara el DTO de confirmación
        UserConfirmationDTO confirmationDTO = new UserConfirmationDTO();
        confirmationDTO.setUsername(userDTO.getUsername());
        confirmationDTO.setEmail(userDTO.getEmail());
        confirmationDTO.setRole("USER");
        confirmationDTO.setCreatedAt(LocalDateTime.now());
        
        // Enviar la confirmación
        sendConfirmationMessage(confirmationDTO);
        
        return confirmationDTO;
    }

    @Override
    public UserConfirmationDTO registerEmployeeWithConfirmation(RegisterDto registerDto) {
        // Primero registramos el empleado normalmente
        UserDTO userDTO = registerEmployee(registerDto);
        
        // Creamos y enviamos la confirmación
        UserConfirmationDTO confirmationDTO = new UserConfirmationDTO();
        confirmationDTO.setUsername(userDTO.getUsername());
        confirmationDTO.setEmail(userDTO.getEmail());
        confirmationDTO.setRole("EMPLOYEE");
        confirmationDTO.setCreatedAt(LocalDateTime.now());
        
        // Enviar la confirmación
        sendConfirmationMessage(confirmationDTO);
        
        return confirmationDTO;
    }

    // Método para enviar mensaje de confirmación al trigger
    @Override
    public void sendConfirmationMessage(UserConfirmationDTO confirmationDTO) {
        try {
            // Prepara y envía el mensaje al trigger Azure
            String jsonBody = String.format(
                "{\"username\":\"%s\",\"email\":\"%s\",\"role\":\"%s\"}",
                confirmationDTO.getUsername(),
                confirmationDTO.getEmail(),
                confirmationDTO.getRole()
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Crear la petición HTTP
            HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);
            
            System.out.println("Enviando confirmación de creación de usuario:");
            System.out.println("URL: " + confirmationFunctionUrl);
            System.out.println("Datos: " + jsonBody);

            // Enviar la petición al trigger
            ResponseEntity<String> response = restTemplate.postForEntity(
                confirmationFunctionUrl,
                request,
                String.class
            );
            
            // Guardar la respuesta en el DTO
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                confirmationDTO.setConfirmationMessage(response.getBody());
            }
            
            System.out.println("Respuesta del trigger: " + response.getStatusCode());

        } catch (Exception e) {
            System.err.println("Error al enviar confirmación: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

}
