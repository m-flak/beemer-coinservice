package com.beemer.coinservice.infrastructure;

import org.springframework.stereotype.Component;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.DefaultGasProvider;

import com.beemer.coinservice.contracts.Beemer;
import com.beemer.coinservice.contracts.FeeVault;
import com.beemer.coinservice.contracts.IERC20;

@Component
public class ContractLoader {

	private final Credentials credentials;
	private final ContractGasProvider gasProvider = new DefaultGasProvider();

	public ContractLoader(Credentials credentials) {
		this.credentials = credentials;
	}

	public Beemer loadBeemer(String address, Web3j web3) {
		return Beemer.load(address, web3, credentials, gasProvider);
	}

	public FeeVault loadFeeVault(String address, Web3j web3) {
		return FeeVault.load(address, web3, credentials, gasProvider);
	}

	public IERC20 loadIERC20(String address, Web3j web3) {
		return IERC20.load(address, web3, credentials, gasProvider);
	}
}
