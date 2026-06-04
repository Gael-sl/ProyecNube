package com.garage.util;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class CryptoUtil {
    private static final String KEY_STRING = "MySuperSecretKey"; // 16 bytes

    public static String decryptAES(String base64EncryptedPayload) throws Exception {
        byte[] keyBytes = KEY_STRING.getBytes(StandardCharsets.UTF_8);
        byte[] decoded = Base64.getDecoder().decode(base64EncryptedPayload);

        if (decoded.length < 16) {
            throw new IllegalArgumentException("Payload too short. Missing IV.");
        }

        // 1. Extraer el Vector de Inicialización (IV) de 16 bytes al inicio del payload
        byte[] ivBytes = new byte[16];
        System.arraycopy(decoded, 0, ivBytes, 0, 16);

        // 2. Extraer el criptograma
        byte[] ciphertext = new byte[decoded.length - 16];
        System.arraycopy(decoded, 16, ciphertext, 0, ciphertext.length);

        // 3. Inicializar el Cifrador para Descifrado (AES-128 CBC con PKCS5Padding)
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

        // 4. Realizar el descifrado y convertir a cadena String
        byte[] decryptedBytes = cipher.doFinal(ciphertext);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }
}
