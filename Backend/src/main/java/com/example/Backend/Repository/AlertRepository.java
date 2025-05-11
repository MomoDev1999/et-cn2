package com.example.Backend.Repository;

import com.example.Backend.Model.Alert;
import com.example.Backend.Model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByUserOrderByCreatedAtDesc(UserEntity user);
    List<Alert> findAllByOrderByCreatedAtDesc();
} 