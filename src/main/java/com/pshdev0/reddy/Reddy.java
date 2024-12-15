package com.pshdev0.reddy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class Reddy {
    private static Jedis jedis;
    private static String redisKeyPrefix;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static long defaultMillis = 75;

    private Reddy() { }

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

    public static <T> T getCachedOrComputeAndWait(Supplier<T> func,
                                                  Class<T> clazz,
                                                  String redisMethodName,
                                                  String ... stringsToMakeRedisHash) {
        return getCachedOrComputeAndWait(func, clazz, defaultMillis, redisMethodName, stringsToMakeRedisHash);
    }

    public static <T> T getCachedOrComputeAndWait(Supplier<T> func,
                                                  Class<T> clazz,
                                                  long millisToWaitAfterCompute,
                                                  String redisMethodName,
                                                  String ... stringsToMakeRedisHash) {
        var key = createRedisKey(redisMethodName, stringsToMakeRedisHash);
        var redis = getRedis();
        if(redis.exists(key)) {
            try {
                return objectMapper.readValue(redis.get(key), clazz);
            } catch (JsonProcessingException e) {
                System.out.println("redis read failed, computing instead");
            }
        }

        var value = func.get();
        nonBlockingWait(millisToWaitAfterCompute);
        try {
            redis.set(key, objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            System.out.println("could not set redis key/value");
        }
        return value;
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

    public static String getCachedOrPostAndWait(String url, String requestBody) {
        return getCachedOrPostAndWait(url, requestBody, defaultMillis);
    }

    public static String getCachedOrPostAndWait(String url, String requestBody, long millisToWaitAfterPost) {
        return getCachedOrComputeAndWait(() -> {
            List<String> curlCommand = List.of(
                    "curl", "-X", "POST", url,
                    "-H", "Content-Type: application/json",
                    "-d", requestBody
            );

            try {
                ProcessBuilder processBuilder = new ProcessBuilder(curlCommand);
                Process process = processBuilder.start();

                try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String inputLine;
                    StringBuilder response = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    return response.toString();
                }
            } catch (IOException e) {
                System.out.println("post error");
            }

            return null;
        }, String.class, millisToWaitAfterPost, "POST", url, requestBody);
    }

    public static String getCachedOrGetAndWait(String url) {
        return getCachedOrGetAndWait(url, defaultMillis);
    }

    public static String getCachedOrGetAndWait(String url, long millisToWaitAfterGet) {
        return getCachedOrComputeAndWait(() -> {
            List<String> curlCommand = List.of("curl", "-X", "GET", url, "-H", "Content-Type: application/json");

            try {
                ProcessBuilder processBuilder = new ProcessBuilder(curlCommand);
                Process process = processBuilder.start();

                try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String inputLine;
                    StringBuilder response = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }

                    return response.toString();
                }

            } catch (IOException e) {
                System.out.println("get call error");
            }

            return null;
        }, String.class, millisToWaitAfterGet, "GET", url);
    }

    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public static void setDefaultMillis(long defaultMillis) {
        Reddy.defaultMillis = defaultMillis;
    }

    public static long getDefaultMillis() {
        return defaultMillis;
    }

    public static void nonBlockingWait(long milliseconds) {
        System.out.println("Waiting for " + milliseconds + "ms...");
        CompletableFuture.delayedExecutor(milliseconds, TimeUnit.MILLISECONDS).execute(() -> {});
    }
}
