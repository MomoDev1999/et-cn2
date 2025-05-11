package com.example.Backend.Service.Imple;

import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Backend.Model.Rol;
import com.example.Backend.Model.UserEntity;
import com.example.Backend.Repository.UserRepository;

// Define esta clase como un servicio de Spring para manejar detalles de usuario
@Service("userDetailService")
// Configura las transacciones como solo lectura por defecto
@Transactional(readOnly=true)
public class UserDetailsServiceImpl implements UserDetailsService{
    
    // Inyecta el repositorio para acceder a los datos de usuarios
    @Autowired
    private UserRepository userRepository;

    // Método que carga un usuario por su email para la autenticación
    @Override
    @Transactional(readOnly=true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Busca el usuario por email o lanza excepción si no existe
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado!"));
        
        // Lista para almacenar los roles como autoridades de Spring Security
        ArrayList<GrantedAuthority> roles = new ArrayList<>();
        
        // Convierte cada rol del usuario en una autoridad
        for (Rol rol : user.getRoles()) {
            roles.add(new SimpleGrantedAuthority(rol.getName()));
        }
        
        // Crea y retorna un User de Spring Security con las credenciales y roles
        return new User(user.getEmail() , user.getPassword(), roles);
    }
}