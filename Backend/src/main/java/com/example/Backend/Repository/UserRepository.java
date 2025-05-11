package com.example.Backend.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.Backend.Model.UserEntity;


@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long>{
    
    Optional<UserEntity> findByEmail(String email);
    Boolean existsByEmail(String email);

    /**
     * Verifica si existe un usuario con el nombre de usuario especificado
     * @param username Nombre de usuario a verificar
     * @return true si existe, false en caso contrario
     */
    boolean existsByUsername(String username);
}
