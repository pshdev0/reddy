package com.pshdev0.reddy;

import org.apache.commons.lang3.StringUtils;
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

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction;

public class CachedWeb3 {

    private static final Logger logger = LoggerFactory.getLogger(CachedWeb3.class);
    private static final Map<String, Web3j> webMap = new HashMap<>();
    private static final Map<String, String> archiveNodes = new HashMap<>();

    private static final int CACHE_VERSION = 0;

    /**
     * Housekeeping
     */

    public static void addArchiveNodes(Map<String, String> hashMap) {
        archiveNodes.putAll(hashMap);
    }

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

    /**
     * Cached Web3 convenience methods
     */

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
        }, TransactionReceipt.class, rpcId, "getReceiptByHash", CACHE_VERSION, hash);
    }

    public static List<String> getTransactionHashesInBlock(String rpcId, String rpcUrl, BigInteger block) {
        var blockData = getBlockByNumber(rpcId, rpcUrl, block); // this method is cached
        if (blockData == null) {
            logger.error("getTransactionHashesInBlock error - could not get block by number");
            return null;
        }
        return blockData.getTransactions().stream().map(x -> (Transaction)x.get()).map(Transaction::getHash).toList();
    }

    public static LocalDateTime getBlockTimestamp(String rpcId, String rpcUrl, BigInteger block) {
        var blockData = getBlockByNumber(rpcId, rpcUrl, block); // this method is cached
        if (blockData == null) {
            logger.error("getBlockTimestamp error - could not get block by number");
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(blockData.getTimestamp().longValue()), ZoneId.systemDefault());
    }

    public LocalDateTime getBlockDate(String rpcId, String rpcUrl, BigInteger block) {
        var timestamp = getBlockTimestamp(rpcId, rpcUrl, block);

        if(timestamp != null) {
            Instant instant = Instant.ofEpochMilli(Utils.convertToEpochMillis(timestamp.toString()));
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
        return getLatestBlockNumber(rpcId, rpcUrl, 0);
    }

    public static BigInteger getLatestBlockNumber(String rpcId, String rpcUrl, int redisStateId) {
        if (redisStateId < 0) {
            logger.error("getLatestBlockNumber error - redisStateId must be non-negative");
            return null;
        }

        if (redisStateId == 0) {
            // do not cache
            try {
                return getRPCNode(rpcId, rpcUrl).ethBlockNumber().send().getBlockNumber();
            } catch (Exception ignored) {
                logger.error("getLatestBlockNumber error - could not get block number");
                return null;
            }
        }
        else {
            // cache
            return Network.getCachedOrComputeAndWait(() -> {
                try {
                    return getRPCNode(rpcId, rpcUrl).ethBlockNumber().send().getBlockNumber();
                } catch (Exception ignored) {
                    return null;
                }
            }, BigInteger.class, rpcId, "getLatestBlockNumber", redisStateId);
        }
    }

    public static EthBlock.Block getBlockByNumber(String rpcId, String rpcUrl, BigInteger block) {
        return Network.getCachedOrComputeAndWait(() -> {
                    var node = getRPCNode(rpcId, rpcUrl);
                    var dp = new DefaultBlockParameterNumber(block);
                    var result = node.ethGetBlockByNumber(dp, true).send();
                    return result.getBlock();
                },
                EthBlock.Block.class, rpcId, "getBlockByNumber", CACHE_VERSION, String.valueOf(block));
    }

    /**
     * General cached Web3 convenience methods
     */

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
        }, clazz, chainId, "generalGetCachedOrRetrieveAndWait", CACHE_VERSION, contract, block.toString(), funcName, index.toString(), clazz.getName());
    }
}
