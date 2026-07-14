package security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * AESUtil provides core methods to encrypt and decrypt byte arrays (chunks) using AES-256 GCM.
 * 
 * DESIGN RATIONALE FOR INTERVIEWS:
 * 1. Mode: AES/GCM/NoPadding (Galois/Counter Mode).
 * 2. Why GCM? Unlike older modes like CBC, GCM is an AEAD (Authenticated Encryption with Associated Data) mode.
 *    It provides both *confidentiality* (hiding data) and *integrity* (verifying it hasn't been altered).
 * 3. Authentication Tag: GCM appends a 128-bit authentication tag at the end of the ciphertext.
 *    If an attacker flips a single bit of the encrypted image, the tag validation fails on decryption, 
 *    throwing an AEADBadTagException instead of outputting corrupted data. This prevents tampering.
 * 4. No Padding: GCM functions under the hood like a stream cipher (using Counter mode), so it does not
 *    require padding (unlike block modes like CBC), removing vulnerability to padding oracle attacks.
 */
public class AESUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    
    // GCM authentication tag size in bits (128 bits is the standard maximum)
    private static final int GCM_TAG_LENGTH = 128;

    /**
     * Encrypts a chunk of raw bytes using AES-256 in GCM mode.
     * 
     * @param data The plaintext data block (e.g. image slice bytes or raw bytes)
     * @param key The derived or generated 256-bit AES secret key
     * @param iv The 12-byte initialization vector (must be unique for each encryption run)
     * @return byte[] The encrypted data (ciphertext) containing the GCM authentication tag
     * @throws Exception if encryption fails (invalid key, algorithm, or parameters)
     */
    public static byte[] encryptChunk(byte[] data, SecretKey key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        
        // GCMParameterSpec takes the authentication tag size (in bits) and the IV
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        return cipher.doFinal(data);
    }

    /**
     * Decrypts an encrypted chunk of bytes using AES-256 in GCM mode.
     * 
     * @param encryptedData The ciphertext (containing the GCM authentication tag at the end)
     * @param key The 256-bit AES secret key matching the encryption key
     * @param iv The initialization vector used when encrypting this specific chunk
     * @return byte[] The decrypted raw plaintext bytes
     * @throws Exception if decryption or tag authentication fails (e.g. key mismatch or modified data)
     */
    public static byte[] decryptChunk(byte[] encryptedData, SecretKey key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        
        // GCMParameterSpec must match the tag size and IV used during encryption
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        return cipher.doFinal(encryptedData);
    }
}