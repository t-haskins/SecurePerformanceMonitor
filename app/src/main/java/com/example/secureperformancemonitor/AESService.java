package com.example.secureperformancemonitor;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import android.util.Base64;

/*
AES Service for data decryption
Author: Kyle Holman
Edited: Tyler Haskins
*/

public class AESService
{
    // tag used for methods to write logs
    private final static String TAG = "AESService";

    public static void main(String[] args) throws Exception {
        if (true) {
            String plainText = "sometxt";
            SecretKey secKey = getSecretKey();
            String textKey = Base64.encodeToString(secKey.getEncoded(),2);
            System.out.println(textKey);
            byte[] encryptedData = encryptData(plainText, secKey);
            String enc = Base64.encodeToString(encryptedData,2);
            System.out.println(enc);
            System.out.println("\n\n");
        }
    }

    public static SecretKey getSecretKey() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        return generator.generateKey();
    }

    public static byte[] encryptData(String plaintext, SecretKey secKey) throws Exception {
        Cipher aesCipher = Cipher.getInstance("AES");
        aesCipher.init(Cipher.ENCRYPT_MODE,secKey);
        return aesCipher.doFinal(plaintext.getBytes());
    }

    public String decryptData(byte[] encryptedData, SecretKey secKey) throws Exception {
        Cipher aesCipher = Cipher.getInstance("AES");
        aesCipher.init(Cipher.DECRYPT_MODE,secKey);
        return new String(aesCipher.doFinal(encryptedData));
    }
}
