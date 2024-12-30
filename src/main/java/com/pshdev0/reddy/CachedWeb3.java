package com.pshdev0.reddy;

import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Uint;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Security;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction;

public class CachedWeb3 {

    private static final Logger logger = LoggerFactory.getLogger(CachedWeb3.class);
    private static final Map<String, Web3j> webMap = new HashMap<>();
    private static final Map<String, String> archiveNodes = new HashMap<>();

    private static final int CACHE_VERSION = 0;

    public static void addArchiveNodes(Map<String, String> hashMap) {
        archiveNodes.putAll(hashMap);
    }

    public static Transaction getTransactionByHash(String rpcId, String rpcUrl, String hash) {
        return Network.getCachedOrComputeAndWait(() -> {

            var ethTransaction = getRPCNode(rpcId, rpcUrl).ethGetTransactionByHash(hash).send();
            if (ethTransaction.hasError()) {
                logger.error("error retrieving transaction by hash: " + ethTransaction.getError().getMessage());
                return null;
            }
            return ethTransaction.getResult();
        }, Transaction.class, rpcId, "getTxByHash", CACHE_VERSION, hash);
    }

    public static TransactionReceipt getReceiptByHash(String rpcId, String rpcUrl, String hash) {
        return Network.getCachedOrComputeAndWait(() -> {
            var receiptResponse = getRPCNode(rpcId, rpcUrl).ethGetTransactionReceipt(hash).send();
            if (receiptResponse.hasError()) {
                logger.error("error retrieving receipt by hash: " + receiptResponse.getError().getMessage());
                return null;
            }
            return receiptResponse.getResult();
        }, TransactionReceipt.class, rpcId, "getTxByHash", CACHE_VERSION, hash);
    }

    public static ListOfStrings getTransactionHashesInBlock(String rpcId, String rpcUrl, String block) {
        return Network.getCachedOrComputeAndWait(() -> {
            System.out.println("rpcId: " + rpcId);
            System.out.println("rpcUrl: " + rpcUrl);
            System.out.println("block: " + block);

            Web3j web3j;
            try {
                web3j = getRPCNode(rpcId, rpcUrl);
            } catch (Exception e) {
                return null;
            }
            BigInteger blockNumber = new BigInteger(block);

            EthBlock ethBlock;
            try {
                ethBlock = web3j.ethGetBlockByNumber(new DefaultBlockParameterNumber(blockNumber), false).send();
            } catch (IOException e) {
                return null;
            }

            var transactions = ethBlock.getBlock().getTransactions();
            return new ListOfStrings(new ArrayList<>(transactions.stream().map(x -> (String) x.get()).toList()));
        }, ListOfStrings.class, rpcId, "getTxHashesAtBlock", CACHE_VERSION, block);
    }

    public static LocalDateTime getBlockTimestamp(String rpcId, String rpcUrl, BigInteger blockNumber) {
        return Network.getCachedOrComputeAndWait(() -> {
            try {
                var web3j = getRPCNode(rpcId, rpcUrl);

                EthBlock.Block block = web3j.ethGetBlockByNumber(new DefaultBlockParameterNumber(blockNumber), false)
                        .send()
                        .getBlock();
                return LocalDateTime.ofInstant(Instant.ofEpochSecond(block.getTimestamp().longValue()), ZoneId.systemDefault());
            } catch (Exception e) {
                logger.error("Error retrieving block timestamp: {}", e.getMessage(), e);
                return null;
            }
        }, LocalDateTime.class, rpcId, "getBlockTimestamp", CACHE_VERSION, String.valueOf(blockNumber));
    }

    public LocalDateTime getBlockDate(String rpcId, String rpcUrl, BigInteger block) {
        var timestamp = getBlockTimestamp(rpcId, rpcUrl, block);

        if(timestamp != null) {
            Instant instant = Instant.ofEpochMilli(convertToEpochMillis(timestamp.toString()));
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        }
        else {
            System.out.println("Error retrieving block date");
            return null;
        }
    }

    public static BigInteger getTokenIntegerBalance(String rpcId, String rpcUrl, String walletAddress, String tokenAddress, BigInteger block) {
        return Network.getCachedOrComputeAndWait(() -> {
            try {
                if(StringUtils.isBlank(walletAddress)) {
                    return null;
                }
                Function function = new Function("balanceOf", List.of(new Address(tokenAddress)), List.of(new org.web3j.abi.TypeReference<Uint256>() {}));
                String encodedFunction = FunctionEncoder.encode(function);

                var response = getRPCNode(rpcId, rpcUrl).ethCall(createEthCallTransaction(
                        walletAddress, walletAddress, encodedFunction), DefaultBlockParameter.valueOf(block)).send();
                var output = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());

                if (!output.isEmpty()) {
                    Uint256 balance = (Uint256) output.get(0);
                    logger.info("   basic balances worked");
                    return balance.getValue();
                }
            } catch (Exception e) {
                logger.error("ethCall error");
            }

            return null;
        }, BigInteger.class, rpcId, "getTokenBalance", CACHE_VERSION, walletAddress, tokenAddress, String.valueOf(block));
    }

    public static BigInteger getEthIntegerBalance(String rpcId, String rpcUrl, BigInteger block, String walletAddress) {
        return Network.getCachedOrComputeAndWait(() -> getRPCNode(rpcId, rpcUrl).ethGetBalance(walletAddress, DefaultBlockParameter.valueOf(block)).send().getBalance(),
                BigInteger.class, rpcId, "ethGetBalance", CACHE_VERSION, walletAddress, String.valueOf(block));
    }

    /*

        originally from EthereumHelper class

     */

    public static boolean ready(String id) {
        return webMap.containsKey(id);
    }

    public static Web3j getRPCNode(String id, String url) throws Exception {
        var key = id + ":" + url;
        if(!ready(key)) {
            webMap.put(key, createRPCNode(id, url));
        }
        return webMap.get(key);
    }

    public static Web3j createRPCNode(String id, String url) throws Exception {
        logger.info("createRPCNode: " + id + ", " + url);

        // the id must be known
        if (!archiveNodes.containsKey(id)) {
            System.out.println("id is required");
            throw new Exception();
        }

        // if the url is blank we use the one associated with the id key
        if (StringUtils.isBlank(url)) {
            System.out.println("using native url for id");
            return Web3j.build(new HttpService(archiveNodes.get(id)));
        }
        else {
            System.out.println("using provided url with provided id");
            return Web3j.build(new HttpService(url));
        }
    }

    public static BigInteger getTransactionCount(String rpcId, String rpcUrl, String wallet, BigInteger block) {
        // returns the nonce of the wallet address at block
        return Network.getCachedOrComputeAndWait(() -> {
            try {
                return getRPCNode(rpcId, rpcUrl).ethGetTransactionCount(wallet, DefaultBlockParameter.valueOf(block)).send().getTransactionCount();
            } catch (Exception e) {
                return null;
            }
        }, BigInteger.class, rpcId, "ethGetTransactionCount", CACHE_VERSION, wallet, String.valueOf(block));
    }

    public static BigInteger getLatestBlockNumber(String rpcId, String rpcUrl) {
        // do not cache this
        try {
            return getRPCNode(rpcId, rpcUrl).ethBlockNumber().send().getBlockNumber();
        } catch (Exception ignored) {
            return null;
        }
    }

//    public static Address getLatest(String chainId, String contract, String funcName) throws Exception {
//        return get(chainId, contract, DefaultBlockParameterName.LATEST, funcName, null, new TypeReference<>() {});
//    }
//
//    public static <T extends Type> T getLatest(String chainId, String contract, String funcName, Uint index, TypeReference<T> typeReference) throws Exception {
//        return get(chainId, contract, DefaultBlockParameterName.LATEST, funcName, index, typeReference);
//    }

    public static <T> T generalGetCachedOrRetrieveAndWait(String chainId, String rpcUrl, String contract, DefaultBlockParameter block, String funcName, Class<T> clazz) throws Exception {
        return generalGetCachedOrRetrieveAndWait(chainId, rpcUrl, contract, block, funcName, null, clazz);
    }

    public static <T> T generalGetCachedOrRetrieveAndWait(String chainId, String rpcUrl, String contract, DefaultBlockParameter block, String funcName, Uint index, Class<T> clazz) throws Exception {
        return Network.getCachedOrComputeAndWait(() -> {
            Function function;
            if (index == null) {
                // note this is web3j TypeReference, not Jackson !
                function = new Function(funcName, List.of(), List.of(new TypeReference<>() {
                    @Override
                    public java.lang.reflect.Type getType() {
                        return clazz;
                    }
                }));
            } else {
                // note this is web3j TypeReference, not Jackson !
                function = new Function(funcName, List.of(index), List.of(new TypeReference<>() {
                    @Override
                    public java.lang.reflect.Type getType() {
                        return clazz;
                    }
                }));
            }

            String encodedFunction = FunctionEncoder.encode(function);
            EthCall response;
            try {
                response = getRPCNode(chainId, rpcUrl).ethCall(createEthCallTransaction(contract, contract, encodedFunction), block).send();
            } catch (Exception e) {
                return null;
            }

            var output = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
            if (output.isEmpty()) {
                return null;
            } else {
                return (T) output.get(0);
            }
        }, clazz, chainId, "Web3Sdk.get", CACHE_VERSION, contract, block.toString(), funcName, index.toString(), clazz.getName());
    }

    /*

        helper functions

     */

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
