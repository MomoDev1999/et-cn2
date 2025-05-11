// Paquete que contiene el controlador de roles
package com.example.Backend.Controller;

// Importaciones necesarias para el controlador
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Backend.DTO.RolDTO;
import com.example.Backend.Model.Rol;
import com.example.Backend.Service.RolService;

// Controlador REST que maneja la gestión de roles
@RestController
@RequestMapping("/api/roles")
public class RolController {

    // Inyección del servicio de roles
    @Autowired
    private RolService rolService;

    // Endpoint para crear un nuevo rol
    @PostMapping("/create_rol")
    public ResponseEntity<Rol> createRol(@RequestBody RolDTO rolDTO) {
        Rol rol = new Rol();
        rol.setName(rolDTO.getName());
        Rol createdRol = rolService.saveRol(rol);
        return new ResponseEntity<>(createdRol, HttpStatus.CREATED);
    }

    // Endpoint para obtener todos los roles del sistema
    @GetMapping("/mostrar_all_roles")
    public ResponseEntity<List<Rol>> getAllRoles() {
        List<Rol> roles = rolService.getAllRoles();
        return new ResponseEntity<>(roles, HttpStatus.OK);
    }

    // Endpoint para actualizar un rol existente
    @PutMapping("/{id}")
    public ResponseEntity<Rol> updateRol(@PathVariable Long id, @RequestBody RolDTO rolDTO) {
        Optional<Rol> rolOptional = rolService.RolById(id);
        if (rolOptional.isPresent()) {
            Rol rol = rolOptional.get();
            rol.setName(rolDTO.getName());
            return new ResponseEntity<>(rolService.updateRol(rol), HttpStatus.OK);
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    // Endpoint para eliminar un rol por su ID
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRol(@PathVariable Long id) {
        if (rolService.RolById(id).isPresent()) {
            rolService.deleteRolById(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
} 