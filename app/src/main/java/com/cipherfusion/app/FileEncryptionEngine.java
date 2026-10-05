package com.cipherfusion.app;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class FileEncryptionEngine {

    public interface ProgressListener {
        void onProgress(long processed, long total);
    }

    private static final byte[] MAGIC = new byte[] {'C', 'F', 'F', '2'};
    private static final int VERSION = 1;
    private static final int SALT_LENGTH = 16;
    private static final int NONCE_PREFIX_LENGTH = 8;
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int PBKDF2_ITERATIONS = 150_000;
    private static final int CHUNK_SIZE = 1024 * 1024;
    private static final int MAX_NAME_LENGTH = 64 * 1024;

    private FileEncryptionEngine() {}

    public static void encrypt(
            Context context,
            Uri inputUri,
            Uri outputUri,
            String password,
            String originalName,
            ProgressListener listener
    ) throws Exception {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Encryption key cannot be empty.");
        }

        ContentResolver resolver = context.getContentResolver();
        long total = getSize(resolver, inputUri);

        byte[] salt = new byte[SALT_LENGTH];
        byte[] noncePrefix = new byte[NONCE_PREFIX_LENGTH];
        SecureRandom random = new SecureRandom();
        random.nextBytes(salt);
        random.nextBytes(noncePrefix);

        SecretKey key = deriveKey(password, salt);

        try (InputStream rawIn = resolver.openInputStream(inputUri);
             OutputStream rawOut = resolver.openOutputStream(outputUri)) {

            if (rawIn == null) throw new IOException("Unable to open input file.");
            if (rawOut == null) throw new IOException("Unable to open output file.");

            DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(rawOut, CHUNK_SIZE));

            writeHeader(out, salt, noncePrefix, originalName);

            BufferedInputStream in = new BufferedInputStream(rawIn, CHUNK_SIZE);
            byte[] plain = new byte[CHUNK_SIZE];

            long processed = 0;
            int chunkIndex = 0;
            int read;

            while ((read = readChunk(in, plain)) > 0) {
                byte[] nonce = buildNonce(noncePrefix, chunkIndex);
                byte[] aad = buildAad(chunkIndex, read);

                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, key,
                        new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
                cipher.updateAAD(aad);

                byte[] encrypted = cipher.doFinal(plain, 0, read);

                out.writeInt(read);
                out.writeInt(encrypted.length);
                out.write(encrypted);

                processed += read;
                if (listener != null) listener.onProgress(processed, total);

                chunkIndex++;
            }

            out.writeInt(0);
            out.writeInt(0);
            out.flush();
        }
    }

    public static void decrypt(
            Context context,
            Uri inputUri,
            Uri outputUri,
            String password,
            ProgressListener listener
    ) throws Exception {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Decryption key cannot be empty.");
        }

        ContentResolver resolver = context.getContentResolver();
        long total = getEncryptedPlaintextSize(resolver, inputUri);

        try (InputStream rawIn = resolver.openInputStream(inputUri);
             OutputStream rawOut = resolver.openOutputStream(outputUri)) {

            if (rawIn == null) throw new IOException("Unable to open encrypted file.");
            if (rawOut == null) throw new IOException("Unable to open output file.");

            DataInputStream in = new DataInputStream(
                    new BufferedInputStream(rawIn, CHUNK_SIZE));
            BufferedOutputStream out = new BufferedOutputStream(rawOut, CHUNK_SIZE);

            Header header = readHeader(in);
            SecretKey key = deriveKey(password, header.salt);

            byte[] encrypted = new byte[CHUNK_SIZE + 32];
            long processed = 0;
            int expectedIndex = 0;

            while (true) {
                int plainLength = in.readInt();
                int encryptedLength = in.readInt();

                if (plainLength == 0 && encryptedLength == 0) {
                    break;
                }

                if (plainLength < 1 || plainLength > CHUNK_SIZE) {
                    throw new IOException("Invalid encrypted chunk.");
                }
                if (encryptedLength < plainLength + 16 ||
                        encryptedLength > CHUNK_SIZE + 16) {
                    throw new IOException("Invalid encrypted chunk length.");
                }

                if (encrypted.length < encryptedLength) {
                    encrypted = new byte[encryptedLength];
                }

                in.readFully(encrypted, 0, encryptedLength);

                byte[] nonce = buildNonce(header.noncePrefix, expectedIndex);
                byte[] aad = buildAad(expectedIndex, plainLength);

                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key,
                        new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
                cipher.updateAAD(aad);

                byte[] plain = cipher.doFinal(encrypted, 0, encryptedLength);

                if (plain.length != plainLength) {
                    throw new IOException("Authentication/length verification failed.");
                }

                out.write(plain);
                processed += plain.length;

                if (listener != null) listener.onProgress(processed, total);
                expectedIndex++;
            }

            out.flush();
        }
    }

    private static void writeHeader(
            DataOutputStream out,
            byte[] salt,
            byte[] noncePrefix,
            String originalName
    ) throws IOException {
        byte[] name = originalName == null
                ? "encrypted_file".getBytes(StandardCharsets.UTF_8)
                : originalName.getBytes(StandardCharsets.UTF_8);

        if (name.length > MAX_NAME_LENGTH) {
            throw new IOException("Original filename is too long.");
        }

        out.write(MAGIC);
        out.writeInt(VERSION);
        out.writeInt(CHUNK_SIZE);
        out.writeInt(salt.length);
        out.write(salt);
        out.writeInt(noncePrefix.length);
        out.write(noncePrefix);
        out.writeInt(name.length);
        out.write(name);
    }

    private static Header readHeader(DataInputStream in) throws IOException {
        byte[] magic = new byte[4];
        in.readFully(magic);

        if (!Arrays.equals(MAGIC, magic)) {
            throw new IOException("Not a CipherFusion CFF2 file.");
        }

        int version = in.readInt();
        if (version != VERSION) {
            throw new IOException("Unsupported CFF2 version: " + version);
        }

        int chunkSize = in.readInt();
        if (chunkSize != CHUNK_SIZE) {
            throw new IOException("Unsupported chunk size.");
        }

        int saltLength = in.readInt();
        if (saltLength != SALT_LENGTH) {
            throw new IOException("Invalid salt.");
        }

        byte[] salt = new byte[saltLength];
        in.readFully(salt);

        int prefixLength = in.readInt();
        if (prefixLength != NONCE_PREFIX_LENGTH) {
            throw new IOException("Invalid nonce prefix.");
        }

        byte[] prefix = new byte[prefixLength];
        in.readFully(prefix);

        int nameLength = in.readInt();
        if (nameLength < 0 || nameLength > MAX_NAME_LENGTH) {
            throw new IOException("Invalid original filename.");
        }

        byte[] name = new byte[nameLength];
        in.readFully(name);

        return new Header(salt, prefix, new String(name, StandardCharsets.UTF_8));
    }

    private static SecretKey deriveKey(String password, byte[] salt)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(),
                salt,
                PBKDF2_ITERATIONS,
                KEY_LENGTH_BITS);

        try {
            SecretKeyFactory factory =
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } finally {
            spec.clearPassword();
        }
    }

    private static byte[] buildNonce(byte[] prefix, int index) {
        ByteBuffer buffer = ByteBuffer.allocate(NONCE_LENGTH);
        buffer.put(prefix);
        buffer.putInt(index);
        return buffer.array();
    }

    private static byte[] buildAad(int index, int plainLength) {
        ByteBuffer buffer = ByteBuffer.allocate(12);
        buffer.putInt(VERSION);
        buffer.putInt(index);
        buffer.putInt(plainLength);
        return buffer.array();
    }

    private static int readChunk(InputStream in, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int n = in.read(buffer, offset, buffer.length - offset);
            if (n == -1) break;
            if (n == 0) continue;
            offset += n;
        }
        return offset;
    }

    private static long getSize(ContentResolver resolver, Uri uri) {
        try (android.database.Cursor cursor = resolver.query(
                uri, new String[]{"_size"}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex("_size");
                if (index >= 0 && !cursor.isNull(index)) return cursor.getLong(index);
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private static long getEncryptedPlaintextSize(
            ContentResolver resolver, Uri uri) {
        return -1;
    }

    private static final class Header {
        final byte[] salt;
        final byte[] noncePrefix;
        final String originalName;

        Header(byte[] salt, byte[] noncePrefix, String originalName) {
            this.salt = salt;
            this.noncePrefix = noncePrefix;
            this.originalName = originalName;
        }
    }
}
