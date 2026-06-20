package com.beemer.coinservice.chaintask.epoch;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.tx.gas.DefaultGasProvider;

import com.beemer.coinservice.chaintask.ChainTask;
import com.beemer.coinservice.client.PinataClient;
import com.beemer.coinservice.config.BlockchainProperties;
import com.beemer.coinservice.contracts.Beemer;
import com.beemer.coinservice.utils.StandardMerkleTree;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CreateEpoch implements ChainTask {
    private static final String BEEMER = "Beemer";

    private BlockchainProperties.Chain chainData = null;

    @Autowired
    private BlockchainProperties blockchainProperties;

    @Autowired
    private Map<Long, Web3j> web3jInstances;

    @Autowired
    private Credentials web3jCredentials;

    @Autowired
    private PinataClient pinata;

    @Override
    public void execute(Long forChain) {
        var web3 = web3jInstances.get(forChain);
        
        var beemer = Beemer.load(
            getChainData(forChain).getContracts().get(BEEMER).getAddress(),
            web3jInstances.get(forChain),
            web3jCredentials,
            new DefaultGasProvider()
        );

        try {
            String stage = "Get Block Number";
            var snapshotBlock = web3.ethBlockNumber().send().getBlockNumber();

            stage = "Find Unique Lockers";
            var uniqueLockers = 
                beemer.lockedEventFlowable(
                    DefaultBlockParameter.valueOf(
                        getChainData(forChain).getContracts().get(BEEMER).getCreatedBlock()), 
                    DefaultBlockParameter.valueOf(snapshotBlock)
                ).map(m -> m._of)
                .collect(HashSet<String>::new, Set::add)
                .blockingGet();

            stage = "Determine Locked Balances";
            for (String locker : uniqueLockers) {
                
            }    
        } catch (IOException e) {
            log.error("Ethereum transaction failure.", e);
        }

        // var ipFsUri = uploadMerkleTree(epochId, tree);
        // log.info("Epoch {} created - {}", epochId, ipFsUri);
    }

    private PinataClient.PinResponse uploadMerkleTree(
        int epochId,
        StandardMerkleTree.StandardMerkleTreeData tree
    ) {
        var request = 
            new PinataClient.PinRequest<StandardMerkleTree.StandardMerkleTreeData>(
                tree, 
                null,
                new PinataClient.PinMetadata(String.format("beemer-epoch-%s.json", epochId), new HashMap<String,String>())
            );
        
        log.info("Creating epoch {} and uploading to IPFS...", epochId);
        return pinata.pinJSON(request);
    }

    private BlockchainProperties.Chain getChainData(long chainId) {
        if (chainData == null) {
            var data = blockchainProperties.getChains()
                                .stream()
                                .filter(chain -> chain.getChainId() == chainId)
                                .findFirst();
            
            if (data.isPresent()) {
                chainData = data.get();
            } else {
                throw new IllegalArgumentException("No chain configured for chainId: " + chainId);
            }
        }

        return chainData;
    }
}