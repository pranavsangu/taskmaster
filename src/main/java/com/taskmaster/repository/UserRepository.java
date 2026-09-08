package com.taskmaster.repository;

import java.util.Optional;

import com.taskmaster.domain.User;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
}
