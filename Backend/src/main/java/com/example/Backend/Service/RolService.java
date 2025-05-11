package com.example.Backend.Service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.Backend.Model.Rol;

@Service
public interface RolService {
    
        //buscar rol por nombre
    Optional<Rol> getRolname(String name);
    
    //guardar rol
    Rol saveRol(Rol rol);
    
    //obtener todos los roles
    List<Rol> getAllRoles();
    
    //obtener rol por id
    Optional<Rol> RolById(Long id);
    
    //eliminar rol
    void deleteRolById(Long id);

    //actualizar rol
    Rol updateRol(Rol rol);

}
