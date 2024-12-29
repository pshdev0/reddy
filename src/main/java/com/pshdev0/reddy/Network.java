package com.pshdev0.reddy;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
        var id = Arrays.stream(stringsToJoinAndHash).map(x -> x.replaceAll("\\s+", "")).collect(Collectors.joining()).toLowerCase();
        return redisKeyPrefix + ":" + source + ":" + method + ":" + version + ":" + getKeccak256Hash(id);
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

    public static void showDebugInfo(boolean state) {
        debugInfo = state;
    }

    public static <T> T getCachedOrComputeAndWait(Supplier<T> func,
                                                  Class<T> typeReference,
                                                  String redisSourceName,
                                                  String redisMethodName,
                                                  int redisVersion,
                                                  String ... stringsToMakeRedisHash) {
        return getCachedOrComputeAndWait(func, typeReference, defaultMillis, redisSourceName, redisMethodName, redisVersion, stringsToMakeRedisHash);
    }

    public static <T> T getCachedOrComputeAndWait(Supplier<T> func,
                                                  Class<T> clazz,
                                                  long millisToWaitBeforeCompute,
                                                  String redisSourceName,
                                                  String redisMethodName,
                                                  int redisVersion,
                                                  String ... stringsToMakeRedisHash) {
        var key = createRedisKey(redisSourceName, redisMethodName, redisVersion, stringsToMakeRedisHash);
        var redis = getRedis();

        if (redis.exists(key)) {
            if (redis.get(key) == null) {
                redis.del(key);
            }
            else {
                try {
                    var redisValue = redis.get(key);
                    var returnValue = objectMapper.readValue(redisValue, clazz); // convert the JSON redis string to class T (if possible)
                    if (debugInfo) {
                        System.out.println("redis key: " + key + " value: " + returnValue);
                        System.out.println("hashes:" + Arrays.stream(stringsToMakeRedisHash).map(x -> x.replaceAll("\\s+", "")).collect(Collectors.joining()).toLowerCase());
                    }
                    return returnValue;
                } catch (JsonProcessingException e) {
                    System.out.println("Redis read failed, computing instead");
                } catch (Exception ignored) {
                    System.out.println("failed to read redis value, computing instead");
                }
                redis.del(key); // something went wrong, delete the cached key/value pair
            }
        }

        // at this point any previous key, if any, will no longer exist

        blockingWait(millisToWaitBeforeCompute);
        var value = func.get();

        if (value == null) {
            System.out.println("was going to use redis key " + key + ", but returned value was null");
            System.out.println("hashes:" + Arrays.stream(stringsToMakeRedisHash).map(x -> x.replaceAll("\\s+", "")).collect(Collectors.joining()).toLowerCase());
        }
        else {
            try {
                var valueString = objectMapper.writeValueAsString(value); // convert object to string to store in redis as json string
                redis.set(key, valueString); // store the key
            } catch (JsonProcessingException e) {
                System.out.println("Could not set Redis key/value, but returning non-null value anyway");
                redis.del(key); // just to be safe, delete the key (although it should never be created)
            }
        }

        return value;
    }

    public static <T> T post(String url, String requestBody, Class<T> returnClass) {
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
                return objectMapper.readValue(response.toString(), returnClass);
            }
        } catch (IOException e) {
            System.out.println("post error");
        }

        return null;
    }

    public static <T> T getCachedOrPostAndWait(String sourceId, String url, String requestBody, Class<T> returnClass) {
        return getCachedOrPostAndWait(sourceId, url, requestBody, defaultMillis, returnClass);
    }

    public static <T> T getCachedOrPostAndWait(String sourceId, String url, String requestBody, long millisToWaitBeforePost, Class<T> returnClass) {
        return getCachedOrComputeAndWait(() -> post(url, requestBody, returnClass), returnClass, millisToWaitBeforePost, sourceId, "POST", 0, url, requestBody);
    }

    public static <T> T get(String url, Class<T> returnClass) {
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

                return objectMapper.readValue(response.toString(), returnClass);
            }
        } catch (IOException e) {
            System.out.println("get call error");
        }

        return null;
    }

    public static <T> T getCachedOrGetAndWait(String sourceId, String url, Class<T> returnClass) {
        return getCachedOrGetAndWait(sourceId, url, defaultMillis, returnClass);
    }

    public static <T> T getCachedOrGetAndWait(String sourceId, String url, long millisToWaitBeforeGet, Class<T> returnClass) {
        return getCachedOrComputeAndWait(() -> Network.get(url, returnClass), returnClass, millisToWaitBeforeGet, sourceId, "GET", 0, url);
    }
}
