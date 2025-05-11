// Paquete que contiene la implementación del servicio de roles
package com.example.Backend.Service.Imple;

// Importaciones necesarias
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.Backend.Model.Rol;
import com.example.Backend.Repository.RoleRepository;
import com.example.Backend.Service.RolService;

// Servicio que implementa la lógica de negocio para los roles
@Service
public class RolServiceImple implements RolService {
    
    // Inyección automática del repositorio de roles
    @Autowired
    private RoleRepository roleRepository;

    // Método que busca un rol por su nombre
    @Override
    public Optional<Rol> getRolname(String name) {
        // Retorna un Optional que puede contener el rol si existe
        return roleRepository.findByName(name);
    }

    // Método para guardar un nuevo rol
    @Override
    public Rol saveRol(Rol rol) {
        // Guarda el rol en la base de datos y retorna el rol guardado
        return roleRepository.save(rol);
    }

    // Método para obtener todos los roles
    @Override
    public List<Rol> getAllRoles() {
        // Retorna una lista con todos los roles existentes
        return roleRepository.findAll();
    }

    // Método para buscar un rol por su ID
    @Override
    public Optional<Rol> RolById(Long id) {
        // Retorna un Optional que puede contener el rol si existe
        return roleRepository.findById(id);
    }

    // Método para eliminar un rol por su ID
    @Override
    public void deleteRolById(Long id) {
        // Elimina el rol de la base de datos
        roleRepository.deleteById(id);
    }

    // Método para actualizar un rol existente
    @Override
    public Rol updateRol(Rol rol) {
        // Actualiza el rol en la base de datos y retorna el rol actualizado
        return roleRepository.save(rol);
    }
}
