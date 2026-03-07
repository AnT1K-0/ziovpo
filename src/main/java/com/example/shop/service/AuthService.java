package com.example.shop.service;

import com.example.shop.controller.dto.AuthResponse;
import com.example.shop.controller.dto.LoginRequest;
import com.example.shop.controller.dto.RegisterRequest;
import com.example.shop.controller.dto.RefreshRequest;
import com.example.shop.model.SessionStatus;
import com.example.shop.model.UserAccount;
import com.example.shop.model.UserSession;
import com.example.shop.repository.UserAccountRepository;
import com.example.shop.repository.UserSessionRepository;
import com.example.shop.security.JwtTokenProvider;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserAccountRepository userRepo;
    private final UserSessionRepository sessionRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public void register(RegisterRequest req) {
        userRepo.findByUsername(req.username()).ifPresent(u -> {
            throw new EntityExistsException("User already exists");
        });

        validatePassword(req.password());

        UserAccount user = new UserAccount();
        user.setUsername(req.username());
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setRole("ROLE_USER");
        user.setCreatedAt(java.time.OffsetDateTime.now());
        userRepo.save(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest req, String userAgent, String ipAddress) {
        UserAccount user = userRepo.findByUsername(req.username())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        // 1. Создаем сессию с ВРЕМЕННЫМИ значениями, чтобы пройти NOT NULL в БД
        UserSession session = new UserSession();
        session.setUser(user);
        session.setStatus(SessionStatus.ACTIVE);
        session.setCreatedAt(Instant.now());
        session.setUserAgent(userAgent);
        session.setIpAddress(ipAddress);

        // временный refreshToken и expiresAt, чтобы insert не упал на NOT NULL
        session.setRefreshToken("PENDING");
        session.setExpiresAt(Instant.now().plus(1, ChronoUnit.MINUTES));

        session = sessionRepo.save(session); // здесь уже есть session.getId()

        // 2. Генерируем настоящие токены, используя id сессии
        String accessToken = jwtTokenProvider.generateAccessToken(user, session.getId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user, session.getId());

        // 3. Обновляем сессию реальными данными
        Date refreshExpiry = jwtTokenProvider.getExpiration(refreshToken);
        session.setRefreshToken(refreshToken);
        session.setExpiresAt(refreshExpiry.toInstant());
        sessionRepo.save(session);

        return new AuthResponse(accessToken, refreshToken);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest req, String userAgent, String ipAddress) {
        String refreshToken = req.refreshToken();

        if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        Long sessionId = jwtTokenProvider.getSessionId(refreshToken);
        if (sessionId == null) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        UserSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new EntityNotFoundException("Session not found"));

        // токен из запроса должен совпадать с тем, что лежит в БД
        if (!refreshToken.equals(session.getRefreshToken())) {
            throw new IllegalArgumentException("Refresh token does not match this session");
        }

        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new IllegalStateException("Session is not active");
        }

        if (session.getExpiresAt().isBefore(Instant.now())) {
            session.setStatus(SessionStatus.EXPIRED);
            sessionRepo.save(session);
            throw new IllegalStateException("Session has expired");
        }

        UserAccount user = session.getUser();

        // помечаем старую сессию как использованную
        session.setStatus(SessionStatus.REFRESHED);
        sessionRepo.save(session);

        // создаём новую сессию и новую пару токенов
        UserSession newSession = new UserSession();
        newSession.setUser(user);
        newSession.setStatus(SessionStatus.ACTIVE);
        newSession.setCreatedAt(Instant.now());
        newSession.setUserAgent(userAgent);
        newSession.setIpAddress(ipAddress);

        // временные значения, чтобы пройти NOT NULL
        newSession.setRefreshToken("PENDING");
        newSession.setExpiresAt(Instant.now().plus(1, ChronoUnit.MINUTES));

        newSession = sessionRepo.save(newSession);

        String newAccessToken = jwtTokenProvider.generateAccessToken(user, newSession.getId());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user, newSession.getId());
        Date newRefreshExpiry = jwtTokenProvider.getExpiration(newRefreshToken);

        newSession.setRefreshToken(newRefreshToken);
        newSession.setExpiresAt(newRefreshExpiry.toInstant());
        sessionRepo.save(newSession);

        return new AuthResponse(newAccessToken, newRefreshToken);
    }

    private void validatePassword(String password) {
        if (password.length() < 8) {
            throw new IllegalArgumentException("Password too short");
        }
        if (!password.matches(".*\\d.*")) {
            throw new IllegalArgumentException("Password must contain a digit");
        }
        if (!password.matches(".*[!@#$%^&*().,;:+-].*")) {
            throw new IllegalArgumentException("Password must contain a special symbol");
        }
    }
}
