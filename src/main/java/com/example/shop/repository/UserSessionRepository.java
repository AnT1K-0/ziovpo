package com.example.shop.repository;

import com.example.shop.model.SessionStatus;
import com.example.shop.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    Optional<UserSession> findByRefreshToken(String refreshToken);
    long countByStatus(SessionStatus status);
}