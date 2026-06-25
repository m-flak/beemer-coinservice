package com.beemer.coinservice.chaintask.epoch;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;

import com.beemer.coinservice.chaintask.AbstractChainTask;
import com.beemer.coinservice.chaintask.exception.ChainTaskFailureException;
import com.beemer.coinservice.contracts.Beemer;
import com.beemer.coinservice.contracts.FeeVault;
import com.beemer.coinservice.infrastructure.ContractLoader;
import com.beemer.coinservice.infrastructure.PinataClient;
import com.beemer.coinservice.infrastructure.config.BlockchainProperties;
import com.beemer.coinservice.utils.StandardMerkleTree;

import io.reactivex.Flowable;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CreateEpoch extends AbstractChainTask {
	private static final String BEEMER = "Beemer";

	private final Map<Long, Web3j> web3jInstances;
	private final ContractLoader contractLoader;
	private final PinataClient pinata;
	private final LastScannedBlockStore scanStore;

	@Autowired
	public CreateEpoch(BlockchainProperties blockchainProperties, Map<Long, Web3j> web3jInstances,
			ContractLoader contractLoader, PinataClient pinata, LastScannedBlockStore scanStore) {
		super(blockchainProperties);
		this.web3jInstances = web3jInstances;
		this.contractLoader = contractLoader;
		this.pinata = pinata;
		this.scanStore = scanStore;
	}

	@Override
	public void execute(Long forChain, @Nonnull Object parameters) {
		String stage = "Load Contracts";
		log.trace(stage);
		try {
			var web3 = web3jInstances.get(forChain);
			var beemer = contractLoader.loadBeemer(getChainData(forChain).getContracts().get(BEEMER).getAddress(),
					web3);

			stage = "Get Block Number";
			log.trace(stage);
			var snapshotBlock = web3.ethBlockNumber().send().getBlockNumber();

			stage = "Find Unique Lockers";
			log.trace(stage);
			BigInteger fromBlock = scanStore.get(forChain).map(b -> b.add(BigInteger.ONE))
					.orElse(getChainData(forChain).getContracts().get(BEEMER).getCreatedBlock()).min(snapshotBlock);
			var uniqueLockers = findUniqueLockers(web3, beemer, fromBlock, snapshotBlock);
			scanStore.update(forChain, snapshotBlock);

			stage = "Determine Locked Balances";
			log.trace(stage);
			beemer.setDefaultBlockParameter(DefaultBlockParameter.valueOf(snapshotBlock));
			var addressLockeds = determineLockedBalances(beemer, uniqueLockers);

			BigInteger totalLocked = addressLockeds.stream().map(AddressLocked::locked).reduce(BigInteger.ZERO,
					BigInteger::add);

			if (totalLocked.signum() == 0) {
				log.info("Epoch not created. Nothing locked.");
				return;
			}

			stage = "Get Vault Balances";
			log.trace(stage);
			String feeVaultAddress = getChainData(forChain).getContracts().get("FeeVault").getAddress();
			var vaultBalances = getVaultBalances(web3, feeVaultAddress, getParameter(parameters, "rewardTokens"),
					forChain, snapshotBlock);

			if (vaultBalances.isEmpty()) {
				log.info("Epoch not created. No rewards in vault.");
				return;
			}

			stage = "Create Epochs";
			log.trace(stage);
			var feeVault = contractLoader.loadFeeVault(feeVaultAddress, web3);
			for (var rb : vaultBalances) {
				createEpoch(feeVault, rb, addressLockeds, totalLocked);
			}

		} catch (Exception e) {
			throw new ChainTaskFailureException("Epoch creation failed!", stage, e);
		}
	}

	Set<String> findUniqueLockers(Web3j web3, Beemer beemer, BigInteger createdBlock, BigInteger snapshotBlock)
			throws Exception {
		var chunkSize = BigInteger.valueOf(2000);
		Set<String> lockers = new HashSet<>();

		for (BigInteger from = createdBlock; from.compareTo(snapshotBlock) <= 0; from = from.add(chunkSize)) {
			BigInteger to = from.add(chunkSize).min(snapshotBlock);
			EthFilter filter = new EthFilter(DefaultBlockParameter.valueOf(from), DefaultBlockParameter.valueOf(to),
					beemer.getContractAddress());
			filter.addSingleTopic(EventEncoder.encode(Beemer.LOCKED_EVENT));
			web3.ethGetLogs(filter).send().getLogs().stream()
					.map(logs -> ((EthLog.LogObject) logs.get()).getTopics().get(1))
					.map(topic -> "0x" + topic.substring(26)).forEach(lockers::add);
		}

		return lockers;
	}

	List<AddressLocked> determineLockedBalances(Beemer beemer, Set<String> uniqueLockers) {
		return Flowable.fromIterable(uniqueLockers)
				.flatMap(locker -> beemer.totalBalanceOf(locker).flowable()
						.flatMap(total -> beemer.balanceOf(locker).flowable()
								.map(free -> new AddressLocked(locker, total.subtract(free)))))
				.filter(al -> al.locked().signum() > 0).collect(ArrayList<AddressLocked>::new, List::add).blockingGet();
	}

	List<RewardBalance> getVaultBalances(Web3j web3, String feeVaultAddress, List<?> rewardTokens, Long forChain,
			BigInteger snapshotBlock) {

		return rewardTokens.stream().filter(rt -> forChain.equals(getParameter(rt, "chainId"))).map(rt -> {
			String tokenAddress = getParameter(rt, "rewardToken");
			var token = contractLoader.loadIERC20(tokenAddress, web3);

			token.setDefaultBlockParameter(DefaultBlockParameter.valueOf(snapshotBlock));

			try {
				return new RewardBalance(tokenAddress, token.balanceOf(feeVaultAddress).send());
			} catch (Exception e) {
				log.warn("Failed to get balance for token {}, treating as zero", tokenAddress);
				return new RewardBalance(tokenAddress, BigInteger.ZERO);
			}
		}).filter(rb -> rb.balance().signum() > 0).collect(Collectors.toList());
	}

	void createEpoch(FeeVault feeVault, RewardBalance rb, List<AddressLocked> addressLockeds, BigInteger totalLocked)
			throws Exception {
		var rewards = addressLockeds.stream()
				.map(al -> new AddressReward(al.address(), al.locked().multiply(rb.balance()).divide(totalLocked)))
				.collect(Collectors.toList());

		BigInteger epochAmount = rewards.stream().map(AddressReward::reward).reduce(BigInteger.ZERO, BigInteger::add);

		var leafHashes = rewards.stream().map(
				ar -> StandardMerkleTree.standardLeafHash(List.of(new Address(ar.address()), new Uint256(ar.reward()))))
				.collect(Collectors.toList());
		var tree = StandardMerkleTree.of(leafHashes);

		BigInteger epochId = feeVault.epochCount().send();
		var treeUri = uploadMerkleTree(epochId, tree.dump());

		var receipt = feeVault.createEpoch(rb.token(), epochAmount, tree.getRoot(), treeUri).send();
		log.info("Epoch {} created for token {} in tx {}", epochId, rb.token(), receipt.getTransactionHash());
	}

	private String uploadMerkleTree(BigInteger epochId, StandardMerkleTree.StandardMerkleTreeData tree) {
		var request = new PinataClient.PinRequest<>(tree, null,
				new PinataClient.PinMetadata(String.format("beemer-epoch-%s.json", epochId), new HashMap<>()));

		log.info("Uploading Merkle tree for epoch {} to IPFS...", epochId);

		return "ipfs://" + pinata.pinJSON(request).ipfsHash();
	}

	record AddressLocked(String address, BigInteger locked) {
	}
	record AddressReward(String address, BigInteger reward) {
	}
	record RewardBalance(String token, BigInteger balance) {
	}
}
