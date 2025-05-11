package com.example.Backend.Config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.example.Backend.Security.JwtAuthEntryPoint;
import com.example.Backend.Security.JwtAuthenticationFilter;
import com.example.Backend.Service.Imple.UserDetailsServiceImpl;

// Clase de configuración para la seguridad de la aplicación
@Configuration
@EnableWebSecurity
public class SecurityConfig implements WebMvcConfigurer{

    // Inyección de dependencias necesarias para la seguridad
    @Autowired
    private UserDetailsServiceImpl userDetailsServiceImpl;
    @Autowired
    private JwtAuthEntryPoint jwtAuthEntryPoint;
    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    // Bean para configurar el administrador de autenticación
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    // Bean para configurar el proveedor de autenticación
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider();
        authenticationProvider.setUserDetailsService(userDetailsServiceImpl);
        authenticationProvider.setPasswordEncoder(passwordEncoder());
        return authenticationProvider;
    }

    // Bean para configurar la cadena de filtros de seguridad
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests(auth -> auth
                // Rutas públicas
                .requestMatchers("/api/login", "/api/register/cliente", "/api/register/employee", "/api/home").permitAll()
                // Rutas de actualización
                .requestMatchers("/api/update/client").hasAnyAuthority("USER", "ADMIN")
                .requestMatchers("/api/update/employee").hasAnyAuthority("EMPLOYEE", "ADMIN")
                // Rutas de gestión de usuarios
                .requestMatchers("/api/users").permitAll()
                .requestMatchers("/api/users/{id}").hasAuthority("ADMIN")
                .requestMatchers("/api/users/client/**", "/api/users/employee/**").hasAuthority("ADMIN")
                // Rutas de gestión de roles
                .requestMatchers("/api/roles/**").hasAuthority("ADMIN")
                // Rutas de alertas
                .requestMatchers("/api/alerts/**").hasAuthority("ADMIN")
                .anyRequest().authenticated()
            )
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
    
    // Bean para configurar el codificador de contraseñas
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Bean para configurar CORS (Cross-Origin Resource Sharing)
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.addAllowedOrigin("*"); // Origen del FrontEnd
        configuration.addAllowedMethod("*"); // Permitir todos los métodos (GET, POST, PUT, DELETE)
        configuration.addAllowedHeader("*"); // Permitir todos los encabezados
        configuration.setAllowCredentials(true); // Permitir credenciales
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    // Método para configurar mapeos CORS adicionales
    @SuppressWarnings("null")
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**").allowedOrigins("http://localhost:4200"); // or whatever your frontend URL is
    }
}
