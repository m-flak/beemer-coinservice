package com.beemer.coinservice.chaintask.epoch;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import com.beemer.coinservice.contracts.Beemer;
import com.beemer.coinservice.contracts.FeeVault;
import com.beemer.coinservice.contracts.IERC20;
import com.beemer.coinservice.infrastructure.ContractLoader;
import com.beemer.coinservice.infrastructure.PinataClient;
import com.beemer.coinservice.infrastructure.config.BlockchainProperties;
import com.beemer.coinservice.utils.StandardMerkleTree;

import io.reactivex.Flowable;

@ExtendWith({SpringExtension.class, MockitoExtension.class})
@EnableConfigurationProperties({BlockchainProperties.class})
@ContextConfiguration(initializers = ConfigDataApplicationContextInitializer.class)
@SuppressWarnings("unused")
class CreateEpochTests {
	@Autowired
	BlockchainProperties blockchainProperties;

	@Mock
	ContractLoader contractLoader;

	@Mock
	Map<Long, Web3j> web3jInstances;

	@Mock
	PinataClient pinata;

	@Mock
	Web3j web3j;

	CreateEpoch createEpoch;

	@BeforeEach
	void setUp() {
		createEpoch = new CreateEpoch(blockchainProperties, web3jInstances, contractLoader, pinata,
				new LastScannedBlockStore());
	}

	@Test
	void findUniqueLockers_returnsAddressFromEvent() {
		var mockBeemer = mock(Beemer.class);
		var event = new Beemer.LockedEventResponse();
		event._of = "0xdeadbeef";

		when(mockBeemer.lockedEventFlowable(any(DefaultBlockParameter.class), any(DefaultBlockParameter.class)))
				.thenReturn(Flowable.just(event));

		var result = createEpoch.findUniqueLockers(mockBeemer, BigInteger.ZERO, BigInteger.valueOf(100));

		assertThat(result).containsExactly("0xdeadbeef");
	}

	@Test
	@SuppressWarnings("unchecked")
	void determineLockedBalances_returnsLockedAmount() {
		var mockBeemer = mock(Beemer.class);

		RemoteFunctionCall<BigInteger> totalCall = mock(RemoteFunctionCall.class);
		when(totalCall.flowable()).thenReturn(Flowable.just(BigInteger.valueOf(100)));
		when(mockBeemer.totalBalanceOf("0xabc")).thenReturn(totalCall);

		RemoteFunctionCall<BigInteger> freeCall = mock(RemoteFunctionCall.class);
		when(freeCall.flowable()).thenReturn(Flowable.just(BigInteger.valueOf(30)));
		when(mockBeemer.balanceOf("0xabc")).thenReturn(freeCall);

		var result = createEpoch.determineLockedBalances(mockBeemer, Set.of("0xabc"));

		assertThat(result).hasSize(1);
		assertThat(result.get(0).address()).isEqualTo("0xabc");
		assertThat(result.get(0).locked()).isEqualTo(BigInteger.valueOf(70));
	}

	@Test
	@SuppressWarnings("unchecked")
	void determineLockedBalances_excludesZeroLockedAddresses() {
		var mockBeemer = mock(Beemer.class);

		RemoteFunctionCall<BigInteger> totalCall = mock(RemoteFunctionCall.class);
		when(totalCall.flowable()).thenReturn(Flowable.just(BigInteger.valueOf(50)));
		when(mockBeemer.totalBalanceOf("0xabc")).thenReturn(totalCall);

		RemoteFunctionCall<BigInteger> freeCall = mock(RemoteFunctionCall.class);
		when(freeCall.flowable()).thenReturn(Flowable.just(BigInteger.valueOf(50)));
		when(mockBeemer.balanceOf("0xabc")).thenReturn(freeCall);

		var result = createEpoch.determineLockedBalances(mockBeemer, Set.of("0xabc"));

		assertThat(result).isEmpty();
	}

	@Test
	@SuppressWarnings("unchecked")
	void createEpoch_buildsMerkleTreeMatchingExpected() throws Exception {
		var mockFeeVault = mock(FeeVault.class);

		RemoteFunctionCall<BigInteger> epochCountCall = mock(RemoteFunctionCall.class);
		when(epochCountCall.send()).thenReturn(BigInteger.ZERO);
		when(mockFeeVault.epochCount()).thenReturn(epochCountCall);

		RemoteFunctionCall<TransactionReceipt> createEpochCall = mock(RemoteFunctionCall.class);
		TransactionReceipt receipt = mock(TransactionReceipt.class);

		when(receipt.getTransactionHash()).thenReturn("0xdeadbeef");
		when(createEpochCall.send()).thenReturn(receipt);
		when(mockFeeVault.createEpoch(any(), any(), any(), any())).thenReturn(createEpochCall);

		when(pinata.pinJSON(any())).thenReturn(new PinataClient.PinResponse("QmTest123", 0, "", false));

		List<CreateEpoch.AddressLocked> addressLockeds = List.of(
				new CreateEpoch.AddressLocked("0x000000000000000000000000000000000000aaaa", BigInteger.valueOf(60)),
				new CreateEpoch.AddressLocked("0x000000000000000000000000000000000000bbbb", BigInteger.valueOf(40)));

		CreateEpoch.RewardBalance rb = new CreateEpoch.RewardBalance("0x000000000000000000000000000000000000cccc",
				BigInteger.valueOf(1000));

		BigInteger totalLocked = BigInteger.valueOf(100);
		createEpoch.createEpoch(mockFeeVault, rb, addressLockeds, totalLocked);

		ArgumentCaptor<byte[]> rootCaptor = ArgumentCaptor.forClass(byte[].class);
		verify(mockFeeVault).createEpoch(eq(rb.token()), any(), rootCaptor.capture(), any());

		List<byte[]> expectedLeafHashes = addressLockeds.stream().map(al -> {
			BigInteger reward = al.locked().multiply(rb.balance()).divide(totalLocked);

			return StandardMerkleTree.standardLeafHash(List.of(new Address(al.address()), new Uint256(reward)));
		}).collect(Collectors.toList());

		byte[] expectedRoot = StandardMerkleTree.of(expectedLeafHashes).getRoot();

		assertThat(rootCaptor.getValue()).isEqualTo(expectedRoot);
	}

	public record RewardToken(Long chainId, String rewardToken) {
	}

	@Test
	void getVaultBalances_getsTheBalances() throws Exception {
		var rewardToken1 = new RewardToken(80069L, "0x123");
		var rewardToken2 = new RewardToken(80069L, "0x456");

		var mockErc20 = mock(IERC20.class);
		RemoteFunctionCall<BigInteger> balanceCall = mock(RemoteFunctionCall.class);

		when(balanceCall.send()).thenReturn(BigInteger.valueOf(100)).thenReturn(BigInteger.valueOf(200));
		when(mockErc20.balanceOf(anyString())).thenReturn(balanceCall);
		when(contractLoader.loadIERC20(anyString(), any(Web3j.class))).thenReturn(mockErc20);

		var balances = createEpoch.getVaultBalances(web3j, "0xf00", List.of(rewardToken1, rewardToken2), 80069L,
				BigInteger.valueOf(555));

		assertThat(balances).contains(
				new CreateEpoch.RewardBalance(rewardToken1.rewardToken(), BigInteger.valueOf(100)),
				new CreateEpoch.RewardBalance(rewardToken2.rewardToken(), BigInteger.valueOf(200)));
	}

}