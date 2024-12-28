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

public class Network {
    private static Jedis jedis;
    private static String redisKeyPrefix;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static long defaultMillis = 75;
    private static boolean debugInfo;

    private Network() { }

    public static void setRedisKeyPrefix(String prefix) {
        redisKeyPrefix = prefix;
    }

    public static Jedis getRedis() {
        if(jedis == null) {
            jedis = new Jedis("localhost", 6379);
        }
        return jedis;
    }

    public static String createRedisKey(String source, String method, int version, String ... stringsToJoinAndHash) {
        var id = String.join(":", stringsToJoinAndHash);
        return redisKeyPrefix + ":" + source + ":" + method + ":" + version + ":" + getKeccak256Hash(id);
    }

    public static <T> T getCachedOrComputeAndWait(Supplier<T> func,
                                                  TypeReference<T> typeReference,
                                                  String redisSourceName,
                                                  String redisMethodName,
                                                  int redisVersion,
                                                  String ... stringsToMakeRedisHash) {
        return getCachedOrComputeAndWait(func, typeReference, defaultMillis, redisSourceName, redisMethodName, redisVersion, stringsToMakeRedisHash);
    }

    public static <T> T getCachedOrComputeAndWait(Supplier<T> func,
                                                  TypeReference<T> typeReference,
                                                  long millisToWaitBeforeCompute,
                                                  String redisSourceName,
                                                  String redisMethodName,
                                                  int redisVersion,
                                                  String ... stringsToMakeRedisHash) {
        var key = createRedisKey(redisSourceName, redisMethodName, redisVersion, stringsToMakeRedisHash);
        var redis = getRedis();

        if (redis.exists(key)) {
            try {
                var returnValue = objectMapper.readValue(redis.get(key), typeReference);
                if (debugInfo) {
                    System.out.println("redis key: " + key + " value: " + returnValue);
                }
                return returnValue;
            } catch (JsonProcessingException e) {
                System.out.println("Redis read failed, computing instead");
            } catch (Exception ignored) {
                System.out.println("failed to read redis value, computing...");
            }
        }

        blockingWait(millisToWaitBeforeCompute);
        var value = func.get();
        try {
            redis.set(key, objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            System.out.println("Could not set Redis key/value");
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

    public static String post(String url, String requestBody) {
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
    }

    public static String getCachedOrPostAndWait(String sourceId, String url, String requestBody) {
        return getCachedOrPostAndWait(sourceId, url, requestBody, defaultMillis);
    }

    public static String getCachedOrPostAndWait(String sourceId, String url, String requestBody, long millisToWaitBeforePost) {
        return getCachedOrComputeAndWait(() -> post(url, requestBody), new TypeReference<>() {}, millisToWaitBeforePost, sourceId, "POST", 0, url, requestBody);
    }

    public static String get(String url) {
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
    }

    public static String getCachedOrGetAndWait(String sourceId, String url) {
        return getCachedOrGetAndWait(sourceId, url, defaultMillis);
    }

    public static String getCachedOrGetAndWait(String sourceId, String url, long millisToWaitBeforeGet) {
        return getCachedOrComputeAndWait(() -> Network.get(url), new TypeReference<>() {}, millisToWaitBeforeGet, sourceId, "GET", 0, url);
    }

    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public static void setDefaultMillis(long defaultMillis) {
        Network.defaultMillis = defaultMillis;
    }

    public static long getDefaultMillis() {
        return defaultMillis;
    }

    public static void blockingWait(long milliseconds) {
        CompletableFuture.runAsync(() -> {}, CompletableFuture.delayedExecutor(milliseconds, TimeUnit.MILLISECONDS)).join();
    }

    public static <T> TypeReference<T> createJacksonTypeRef(Class<T> clazz) {
        return new TypeReference<>() {
            @Override
            public java.lang.reflect.Type getType() {
                return clazz;
            }
        };
    }

    public static void showDebugInfo(boolean state) {
        debugInfo = state;
    }
}
