package com.pshdev0.reddy;

import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import redis.clients.jedis.Jedis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Security;
import java.util.List;

public class Network {
    private static Jedis jedis;
    private static String redisKeyPrefix;

    private Network() { }

    public static void setRedisKeyPrefix(String prefix) {
        redisKeyPrefix = prefix;
    }

    public static Jedis getRedis() {
        if(jedis == null) {
            jedis = new Jedis("localhost", 6379); // Default Redis port is 6379
        }
        return jedis;
    }

    public static String createRedisKey(String method, String ... stringsToJoinAndHash) {
        var id = String.join(":", stringsToJoinAndHash);
        return redisKeyPrefix + ":" + method + ":" + getKeccak256Hash(id);
    }

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

    public static NetworkResponse<String> post(String url, String requestBody) {
        return post(url, requestBody, false);
    }

    public static NetworkResponse<String> post(String url, String requestBody, boolean debug) {
        // Use ProcessBuilder to run curl with arguments for POST request
        List<String> curlCommand = List.of(
                "curl", "-X", "POST", url,
                "-H", "Content-Type: application/json",
                "-d", requestBody
        );

        var redisHash = createRedisKey("POST", url, requestBody);
        var redis = getRedis();
        if(redis.exists(redisHash)) {
            return new NetworkResponse<>(redis.get(redisHash), true);
        }

        try {
            // Initialize ProcessBuilder with the curl command
            ProcessBuilder processBuilder = new ProcessBuilder(curlCommand);

            // Start the process
            Process process = processBuilder.start();

            // Read the output from the curl command
            try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }

                // Wait for the process to finish
                int responseCode = process.waitFor();

                // Debug info
                if (debug) {
                    System.out.println("Response status code: " + responseCode);
                }

                var rtnString = response.toString();
                redis.set(redisHash, rtnString);
                return new NetworkResponse<>(rtnString, false);
            }

        } catch (IOException | InterruptedException e) {
            System.out.println("post error");
//            e.printStackTrace();
        }
        return new NetworkResponse<>();
    }

    public static NetworkResponse<String> get(String url, boolean debug) {
        // Use ProcessBuilder to run curl with arguments
        List<String> curlCommand = List.of("curl", "-X", "GET", url, "-H", "Content-Type: application/json");

        var redisHash = createRedisKey("GET", url);
        var redis = getRedis();
        if (redis.exists(redisHash)) {
            return new NetworkResponse<>(redis.get(redisHash), true);
        }

        try {
            // Initialize ProcessBuilder with the curl command
            ProcessBuilder processBuilder = new ProcessBuilder(curlCommand);

            // Start the process
            Process process = processBuilder.start();

            // Read the output from the curl command
            try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }

                // Wait for the process to finish
                int responseCode = process.waitFor();

                // Debug info
                if (debug) {
                    System.out.println("Response status code: " + responseCode);
                }

                var rtnString = response.toString();
                redis.set(redisHash, rtnString);
                return new NetworkResponse<>(rtnString, false);
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return new NetworkResponse<>();
    }
}
