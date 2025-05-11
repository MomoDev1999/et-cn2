package com.example.Backend.Service;

import java.util.List;

import org.springframework.http.HttpHeaders;

import com.example.Backend.DTO.AlertDTO;
import com.example.Backend.DTO.JwtResponseDto;
import com.example.Backend.DTO.LoginDto;
import com.example.Backend.DTO.RegisterDto;
import com.example.Backend.DTO.UserConfirmationDTO;
import com.example.Backend.DTO.UserDTO;

public interface  UserService {
    
    //registrar cliente con confirmación
    UserConfirmationDTO registerWithConfirmation(RegisterDto registerDto);
    
    //registrar cliente
    UserDTO register(RegisterDto registerDto);
    
    //login
    JwtResponseDto login(LoginDto loginDto);
    
    //obtener usuario logueado
    UserDTO getLoguedUser(HttpHeaders headers);

    //registrar empleado con confirmación
    UserConfirmationDTO registerEmployeeWithConfirmation(RegisterDto registerDto);

    //registrar empleado
    UserDTO registerEmployee(RegisterDto registerDto);

    //obtener todos los usuarios
    List<UserDTO> getAllUsers();

    //obtener usuario por id
    UserDTO getUserById(Long id);

    //actualizar usuario
    UserDTO updateUser(UserDTO userDTO);

    //eliminar usuario
    void deleteUser(Long id);

    //actualizar cliente con alerta
    AlertDTO updateClient(UserDTO userDTO);

    //actualizar empleado con alerta
    AlertDTO updateEmployee(UserDTO userDTO);

    //obtener historial de alertas por usuario
    List<AlertDTO> getUserAlerts(Long userId);

    //obtener todas las alertas
    List<AlertDTO> getAllAlerts();

    //marcar alerta como leída
    void markAlertAsRead(Long alertId);

    //enviar mensaje de confirmación al trigger
    void sendConfirmationMessage(UserConfirmationDTO confirmationDTO);

    /**
     * Verifica si un nombre de usuario ya existe en la base de datos
     * @param username Nombre de usuario a verificar
     * @return true si el usuario existe, false en caso contrario
     */
    boolean existsByUsername(String username);

    /**
     * Verifica si un correo electrónico ya existe en la base de datos
     * @param email Correo electrónico a verificar
     * @return true si el email existe, false en caso contrario
     */
    boolean existsByEmail(String email);
}
