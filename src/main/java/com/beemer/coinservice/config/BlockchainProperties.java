package com.beemer.coinservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "beemer.blockchains")
public class BlockchainProperties {

    private List<Chain> chains = new ArrayList<>();

    @Data
    public static class Chain {
        private String name;
        private long chainId;
        private String rpcUrl;
        private Map<String, Contract> contracts;

        @Data
        public static class Contract {
            private String address;
            private BigInteger createdBlock;
        }
    }
}
