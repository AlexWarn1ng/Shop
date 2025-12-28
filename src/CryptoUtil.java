import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public final class CryptoUtil {
    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;          // 96-bit IV для GCM
    private static final int TAG_LEN_BITS = 128;   // 16 bytes tag
    private static final SecureRandom RNG = new SecureRandom();

    // 32 байта = AES-256. Храни этот ключ НЕ в коде, а в config/env.
    // Здесь просто пример: Base64 ключ.
    public static SecretKey keyFromBase64(String base64Key) {
        byte[] raw = Base64.getDecoder().decode(base64Key);
        return new SecretKeySpec(raw, "AES");
    }

    public static String encrypt(String plaintext, SecretKey key) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_LEN];
            RNG.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LEN_BITS, iv));

            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // сохраняем iv + ciphertext в одной строке
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);

            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new RuntimeException("Encrypt failed", e);
        }
    }

    public static String decrypt(String base64, SecretKey key) {
        if (base64 == null) return null;
        try {
            byte[] in = Base64.getDecoder().decode(base64);

            byte[] iv = new byte[IV_LEN];
            byte[] ct = new byte[in.length - IV_LEN];
            System.arraycopy(in, 0, iv, 0, IV_LEN);
            System.arraycopy(in, IV_LEN, ct, 0, ct.length);

            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LEN_BITS, iv));

            byte[] pt = cipher.doFinal(ct);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decrypt failed", e);
        }
    }

    private CryptoUtil() {}
}
