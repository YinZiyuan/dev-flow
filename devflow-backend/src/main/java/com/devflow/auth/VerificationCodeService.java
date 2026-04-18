package com.devflow.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;

@Service
public class VerificationCodeService {

    @PersistenceContext private EntityManager em;
    @Value("${app.verification-code.expiration-minutes}") private int expirationMinutes;

    @Transactional
    public String generate(String email) {
        String code = String.format("%06d", new Random().nextInt(1_000_000));
        em.createNativeQuery(
            "INSERT INTO verification_code(id,email,code,expires_at) VALUES(?,?,?,?)")
            .setParameter(1, UUID.randomUUID())
            .setParameter(2, email)
            .setParameter(3, code)
            .setParameter(4, Instant.now().plusSeconds(expirationMinutes * 60L))
            .executeUpdate();
        return code;
    }

    @Transactional
    public boolean verify(String email, String code) {
        int updated = em.createNativeQuery(
            "UPDATE verification_code SET used=true WHERE email=? AND code=? " +
            "AND used=false AND expires_at > NOW()")
            .setParameter(1, email)
            .setParameter(2, code)
            .executeUpdate();
        return updated > 0;
    }
}
