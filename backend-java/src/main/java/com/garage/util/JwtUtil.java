package com.garage.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import java.util.Date;

public class JwtUtil {
    private static final String SECRET = "SecretKeyForSecurityGaraje123";
    private static final String ISSUER = "GarajeDeportivoAPI";
    private static final Algorithm ALGORITHM = Algorithm.HMAC256(SECRET);

    // Tiempo de expiración de 2 horas
    private static final long EXPIRATION_TIME = 2 * 60 * 60 * 1000; 

    public static String generateToken(String username) {
        return JWT.create()
                .withSubject(username)
                .withIssuer(ISSUER)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .sign(ALGORITHM);
    }

    public static String verifyToken(String token) {
        try {
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            JWTVerifier verifier = JWT.require(ALGORITHM)
                    .withIssuer(ISSUER)
                    .build();
            DecodedJWT jwt = verifier.verify(token);
            return jwt.getSubject();
        } catch (Exception e) {
            return null; // Token inválido
        }
    }
}
