package com.example.secureperformancemonitor;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import android.util.Base64;
import android.util.Log;

/*
Password decryption service
Author: Kyle Holman
Edited: Tyler Haskins
*/

public class AppPasswordService {
    // tag used for methods to write logs
    private final static String TAG = "AppPasswordService";

    private final static int iterations = 10000;
    private final static int keyLength = 512;

    public static byte[] saltByte(String salt) {
        return Base64.decode(salt,2);
    }

    public static String hashPassword( String password, final byte[] salt, final int iterations, final int keyLength) {
        try {
            char[] charPass = password.toCharArray();
            password=null;
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
            PBEKeySpec spec = new PBEKeySpec(charPass, salt, iterations, keyLength);
            charPass=null;
            SecretKey secKey = skf.generateSecret(spec);
            spec=null;
            return Base64.encodeToString(secKey.getEncoded(),2);
        }

        catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    public static String doubleHash(String Pass, byte[] salt, int iterations, int keyLength) {
        String round1 = hashPassword(Pass, salt, iterations, keyLength);
        Pass=null;
        return hashPassword(round1, salt, iterations, keyLength);
    }

    public static boolean validate(String pass, String doubleHash1, byte[] salt, int iterations, int keyLength) {
        String EncInputPass = doubleHash(pass, salt, iterations, keyLength);
        pass=null;
        return doubleHash1.equals(EncInputPass);
    }

    public static boolean validateAppUser(String pass){
        String hash = "88Rs815OwoLwQW+yUUR22zNjBnwOlVxUr/NuyItmFxckrAhNpzLRggoHPSZVpOsB5SNaYpTI+xBJQ0GidmaKUA==";
        String salt = "K0HwA7c69VBvlOcx7kOomQ==";

        if (pass.length() == 0) {
            return false;
        }

        byte [] saltb = saltByte(salt);
        String EncInputPass = doubleHash(pass, saltb, iterations, keyLength);
        pass=null;
        return hash.equals(EncInputPass);
    }

    public static String AESstringKey(byte[] byteKey){
        byte[] AESkey = new byte[16];

        for (int i=0; i<16; i++){
            AESkey[i]=byteKey[i];
        }
        byteKey=null;
        return Base64.encodeToString(AESkey,2);
    }

    public static BigInteger getKey(String Pass) {
        String hash = "88Rs815OwoLwQW+yUUR22zNjBnwOlVxUr/NuyItmFxckrAhNpzLRggoHPSZVpOsB5SNaYpTI+xBJQ0GidmaKUA==";
        String salt = "K0HwA7c69VBvlOcx7kOomQ==";
        String RSAStr="aTtvowYGe9jUiH3tRRZsfMPRhmP17cA9m3Zf+jShDYikNyS+/uJxQqNvmzs/9xAczpgIzFNLqcWeISXRPJQQhL6K9gHBMl6oqpt3HBeSyvyz6XiDvUIGm6RKEOrmwpheyuXY3O/VihSzqHjJfY13hHVYwwSFdfAceKLJqGDVw4uijAIGxJqZamSkehan0l1jRMAKmDCm50nMmlmKTNN4/g==";
        byte [] saltb = saltByte(salt);
        if(validate(Pass, hash, saltb, iterations, keyLength)) {
            AESService AES1 = new AESService();
            String singleHash = hashPassword(Pass, saltb, iterations, keyLength);
            Pass=null;
            String AESkey = AESstringKey(singleHash.getBytes());
            byte [] decodedKeyBytes = Base64.decode(AESkey,2);
            AESkey=null;
            SecretKey key = new SecretKeySpec(decodedKeyBytes,0,decodedKeyBytes.length,"AES");
            decodedKeyBytes=null;
            try {
                String RSAkey = AES1.decryptData(Base64.decode(RSAStr,2), key);
                key=null;
                BigInteger pk = new BigInteger(RSAkey);
                RSAkey=null;
                return pk;
            } catch (Exception e) {
                e.printStackTrace();
                return new BigInteger("0");
            }
        }
        else return new BigInteger("0");
    }

    public static void main(String[] args){
        try {
            BigInteger pk = getKey("dummyPass");
            System.out.print(pk);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Failed");
        }
    }
}
