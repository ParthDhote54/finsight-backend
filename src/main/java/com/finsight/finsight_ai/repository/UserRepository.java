package com.finsight.finsight_ai.repository;

import com.finsight.finsight_ai.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;


@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
   // 1. For Logging In: Spring writes --> SELECT * FROM users WHERE email = ?
    Optional<User> findByEmail(String email);


   //2. For Registration Validation: Spring writes->Select 1 FROM users WHERE email = ?
    boolean existsByEmail(String email);


}
