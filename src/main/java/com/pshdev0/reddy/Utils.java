package com.pshdev0.reddy;

import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Security;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class Utils {

    public static String getKeccak256Hash(String signature) {
        Security.addProvider(new BouncyCastleProvider());
        MessageDigest digest = new Keccak.Digest256();
        byte[] encodedhash = digest.digest(signature.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(encodedhash);
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public static Long convertToEpochMillis(String localDateTimeString) {
        // Define the date-time pattern that matches the input string
        DateTimeFormatter formatter1 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

        // Parse the string back into a LocalDateTime object
        LocalDateTime localDateTime = null;
        try {
            localDateTime = LocalDateTime.parse(localDateTimeString, formatter1);
        }
        catch(Exception ignored1) {
            try {
                localDateTime = LocalDateTime.parse(localDateTimeString, formatter2);
            }
            catch(Exception ignored2) {
            }
        }

        // Convert the LocalDateTime to ZonedDateTime with the system's default time zone
        if(localDateTime == null) {
            return 0L;
        }

        ZonedDateTime zonedDateTime = localDateTime.atZone(ZoneId.systemDefault());

        // Convert the ZonedDateTime to epoch milliseconds
        return zonedDateTime.toInstant().toEpochMilli();
    }
}
