 package test;

import security.AESUtil;
import security.KeyGeneratorUtil;
import javax.crypto.SecretKey;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * DashboardTest acts as an automated validation suite.
 * 
 * It runs four core test cases to prove security validation, 
 * parallel pixel accuracy, thread consistency, and socket transmission:
 * 
 * 1. AES-256-GCM authenticated tag check (proves decryption accuracy & protection against wrong password).
 * 2. Key Derivation uniqueness (proves two identical passwords generate different keys due to distinct salts).
 * 3. Parallel consistency validation (proves sequential and parallel filter outputs have identical pixel allocations).
 * 4. Loopback socket validation (proves client-server handshaking and binary stream transports are complete).
 */
public class DashboardTest {

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  PARALLEL & DISTRIBUTED DASHBOARD SYSTEM TESTS   ");
        System.out.println("==================================================");

        boolean allPassed = true;

        try {
            allPassed &= testAESGCMEncryptionDecryption();
            allPassed &= testKeyDerivationUniqueness();
            allPassed &= testImageFilteringConsistency();
            allPassed &= testDistributedSocketCommunication();
        } catch (Exception e) {
            System.err.println("[FATAL ERROR DURING TEST EXECUTION]: " + e.getMessage());
            e.printStackTrace();
            allPassed = false;
        }

        System.out.println("\n==================================================");
        if (allPassed) {
            System.out.println("  ALL TESTS PASSED SUCCESSFULLY! (100% SUCCESS)   ");
            System.exit(0);
        } else {
            System.out.println("  SOME TESTS FAILED. CHECK SYSTEM DIAGNOSTICS.    ");
            System.exit(1);
        }
        System.out.println("==================================================");
    }

    private static boolean testAESGCMEncryptionDecryption() {
        System.out.println("\n[TEST 1] Testing AES-256-GCM Encryption Integrity...");
        try {
            String password = "securePassword123";
            byte[] originalData = "Parallel and Distributed Systems Lab Final Term 2026".getBytes(StandardCharsets.UTF_8);

            byte[] salt = KeyGeneratorUtil.generateSalt(16);
            byte[] iv = KeyGeneratorUtil.generateIV();
            SecretKey key = KeyGeneratorUtil.deriveKeyFromPassword(password, salt);

            // Encrypt
            byte[] ciphertext = AESUtil.encryptChunk(originalData, key, iv);
            System.out.println("  -> Encrypted data successfully. Ciphertext size: " + ciphertext.length + " bytes.");

            // Decrypt with correct key
            byte[] decryptedData = AESUtil.decryptChunk(ciphertext, key, iv);
            boolean match = Arrays.equals(originalData, decryptedData);

            if (match) {
                System.out.println("  -> [PASSED] Decrypted data matches original content.");
            } else {
                System.out.println("  -> [FAILED] Decrypted data does NOT match original content.");
                return false;
            }

            // Decrypt with incorrect key (should fail GCM auth tag check)
            try {
                SecretKey wrongKey = KeyGeneratorUtil.deriveKeyFromPassword("wrongPassword", salt);
                AESUtil.decryptChunk(ciphertext, wrongKey, iv);
                System.out.println("  -> [FAILED] GCM Authenticated decryption succeeded with WRONG password (security breach!).");
                return false;
            } catch (javax.crypto.AEADBadTagException e) {
                System.out.println("  -> [PASSED] AEAD authentication tag check failed as expected on wrong password.");
            }

            return true;
        } catch (Exception e) {
            System.out.println("  -> [FAILED] Exception during encryption test: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private static boolean testKeyDerivationUniqueness() {
        System.out.println("\n[TEST 2] Testing PBKDF2 Key Derivation Salt Uniqueness...");
        try {
            String password = "samePassword";
            byte[] salt1 = KeyGeneratorUtil.generateSalt(16);
            byte[] salt2 = KeyGeneratorUtil.generateSalt(16);

            SecretKey key1 = KeyGeneratorUtil.deriveKeyFromPassword(password, salt1);
            SecretKey key2 = KeyGeneratorUtil.deriveKeyFromPassword(password, salt2);

            boolean keysEqual = Arrays.equals(key1.getEncoded(), key2.getEncoded());
            if (!keysEqual) {
                System.out.println("  -> [PASSED] Same password derived unique keys due to distinct salts (rainbow tables mitigated).");
                return true;
            } else {
                System.out.println("  -> [FAILED] Password derived identical keys despite different salts.");
                return false;
            }
        } catch (Exception e) {
            System.out.println("  -> [FAILED] Exception during key derivation test: " + e.getMessage());
            return false;
        }
    }

    private static boolean testImageFilteringConsistency() {
        System.out.println("\n[TEST 3] Testing Image Filtering Consistency (Seq vs. Parallel)...");
        try {
            // Create a mock RGB test image (100x100 pixels)
            BufferedImage mockImg = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 100; y++) {
                for (int x = 0; x < 100; x++) {
                    mockImg.setRGB(x, y, (x * 2 + y * 2) << 16 | (x * 2) << 8 | (y * 2));
                }
            }

            gui.GUInFilters guiInstance = new gui.GUInFilters();
            
            // Get Grayscale Seq
            BufferedImage graySeq = reflectCallGrayscale(guiInstance, mockImg, false);
            // Get Grayscale Parallel
            BufferedImage grayPar = reflectCallGrayscale(guiInstance, mockImg, true);

            boolean match = compareImages(graySeq, grayPar);
            if (match) {
                System.out.println("  -> [PASSED] Sequential and Parallel Grayscale outputs are pixel-consistent (no race conditions).");
                return true;
            } else {
                System.out.println("  -> [FAILED] Sequential and Parallel Grayscale outputs differ!");
                return false;
            }
        } catch (Exception e) {
            System.out.println("  -> [FAILED] Exception during filter consistency test: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private static boolean testDistributedSocketCommunication() {
        System.out.println("\n[TEST 4] Testing Distributed TCP Socket Node Communication...");
        final int TEST_PORT = 9999;
        final String TEST_PASSWORD = "testPassword";
        final String TEST_META = "100x100 px";
        final byte[] TEST_PAYLOAD = "ScrambledEncryptedImagePayloadBytesHere".getBytes(StandardCharsets.UTF_8);
        
        final boolean[] serverPassed = {false};
        final boolean[] clientPassed = {false};

        // Start background TCP Server
        Thread serverThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(TEST_PORT)) {
                serverSocket.setSoTimeout(3000); // 3 seconds timeout
                try (Socket clientSocket = serverSocket.accept();
                     DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
                     DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())) {
                    
                    // Read variables sent by Client
                    String password = dis.readUTF();
                    String meta = dis.readUTF();
                    long length = dis.readLong();
                    byte[] payload = new byte[(int) length];
                    dis.readFully(payload);

                    boolean dataValid = TEST_PASSWORD.equals(password) &&
                                        TEST_META.equals(meta) &&
                                        Arrays.equals(TEST_PAYLOAD, payload);

                    if (dataValid) {
                        dos.writeUTF("SUCCESS");
                        serverPassed[0] = true;
                    } else {
                        dos.writeUTF("FAIL");
                    }
                }
            } catch (Exception e) {
                System.err.println("Server Test Exception: " + e.getMessage());
            }
        });
        serverThread.start();

        // Let the server socket start up
        try { Thread.sleep(300); } catch (InterruptedException ignored) {}

        // Connect Client Socket
        try (Socket socket = new Socket("127.0.0.1", TEST_PORT);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {
            
            // Send payload details
            dos.writeUTF(TEST_PASSWORD);
            dos.writeUTF(TEST_META);
            dos.writeLong(TEST_PAYLOAD.length);
            dos.write(TEST_PAYLOAD);
            dos.flush();

            // Receive confirmation
            String response = dis.readUTF();
            if ("SUCCESS".equals(response)) {
                clientPassed[0] = true;
            }
        } catch (Exception e) {
            System.err.println("Client Test Exception: " + e.getMessage());
        }

        // Wait for server thread to join
        try { serverThread.join(2000); } catch (InterruptedException ignored) {}

        if (serverPassed[0] && clientPassed[0]) {
            System.out.println("  -> [PASSED] Client successfully connected, transferred handshake, streamed bytes, and verified receipt.");
            return true;
        } else {
            System.out.println("  -> [FAILED] Distributed socket connection failed or timed out.");
            return false;
        }
    }

    private static BufferedImage reflectCallGrayscale(gui.GUInFilters gui, BufferedImage img, boolean parallel) throws Exception {
        java.lang.reflect.Method method;
        if (parallel) {
            method = gui.getClass().getDeclaredMethod("toGrayScaleParallel", BufferedImage.class);
        } else {
            method = gui.getClass().getDeclaredMethod("toGrayScale", BufferedImage.class);
        }
        method.setAccessible(true);
        return (BufferedImage) method.invoke(gui, img);
    }

    private static boolean compareImages(BufferedImage img1, BufferedImage img2) {
        if (img1.getWidth() != img2.getWidth() || img1.getHeight() != img2.getHeight()) return false;
        for (int y = 0; y < img1.getHeight(); y++) {
            for (int x = 0; x < img1.getWidth(); x++) {
                if (img1.getRGB(x, y) != img2.getRGB(x, y)) return false;
            }
        }
        return true;
    }
}