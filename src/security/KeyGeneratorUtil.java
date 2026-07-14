package security;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

/**
 * KeyGeneratorUtil provides cryptographic utility functions to handle AES key generation,
 * dynamic password-based key derivation (PBKDF2), and secure Initialization Vectors (IVs) for AES-GCM.
 * 
 * DESIGN RATIONALE FOR INTERVIEWS:
 * 1. AES-256 requires a 256-bit (32-byte) key. Simply using a plain text string as a key is weak.
 * 2. We use PBKDF2 (Password-Based Key Derivation Function 2) with HMAC-SHA256 to convert human passwords into secure cryptographic keys.
 * 3. We use SecureRandom to generate cryptographically strong, non-deterministic random data for keys and IVs.
 * 4. AES-GCM requires a unique Initialization Vector (IV) for *every* encryption operation to prevent authentication tag reuse attacks.
 */
public class KeyGeneratorUtil {

    // AES key size in bits (256-bit is the industry gold standard)
    private static final int AES_KEY_SIZE = 256;
    
    // GCM recommended IV size is 12 bytes (96 bits) for optimal performance and security
    private static final int GCM_IV_SIZE = 12;

    // PBKDF2 derivation specifications
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATION_COUNT = 10000; // Balancing security and performance
    private static final int KEY_LENGTH_BITS = 256;

    // SecureRandom is non-deterministic, making it resistant to entropy prediction attacks
    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a cryptographically secure random AES-256 secret key.
     * Use case: Generating session keys dynamically on runtime.
     * 
     * @return SecretKey generated 256-bit AES key
     * @throws NoSuchAlgorithmException if the AES generator is unavailable
     */
    public static SecretKey generateRandomKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(AES_KEY_SIZE, secureRandom);
        return keyGen.generateKey();
    }

    /**
     * Derives a secure 256-bit AES key from a human-readable password using PBKDF2.
     * Use case: User inputs a passphrase to encrypt/decrypt their images.
     * 
     * @param password The user-supplied passphrase
     * @param salt A random byte array used to prevent rainbow table attacks
     * @return SecretKey Derived AES key
     * @throws NoSuchAlgorithmException if PBKDF2 algorithm is unavailable
     * @throws InvalidKeySpecException if specifications are invalid
     */
    public static SecretKey deriveKeyFromPassword(String password, byte[] salt) 
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        
        PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(), 
                salt, 
                ITERATION_COUNT, 
                KEY_LENGTH_BITS
        );
        
        SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        
        // Wrap the derived key bytes into an AES SecretKey object
        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Generates a random Initialization Vector (IV) for GCM mode.
     * An IV must NEVER be reused with the same key. Generating it randomly ensures uniqueness.
     * 
     * @return byte[] 12-byte initialization vector
     */
    public static byte[] generateIV() {
        byte[] iv = new byte[GCM_IV_SIZE];
        secureRandom.nextBytes(iv);
        return iv;
    }

    /**
     * Generates a random salt for password-based key derivation.
     * Salt prevents precomputation (rainbow table) attacks by ensuring identical passwords yield different keys.
     * 
     * @param size Size of the salt in bytes (16 bytes is recommended)
     * @return byte[] generated salt
     */
    public static byte[] generateSalt(int size) {
        byte[] salt = new byte[size];
        secureRandom.nextBytes(salt);
        return salt;
    }
}