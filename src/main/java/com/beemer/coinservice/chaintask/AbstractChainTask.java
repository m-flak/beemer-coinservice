package com.beemer.coinservice.chaintask;

import java.util.Objects;

import com.beemer.coinservice.infrastructure.config.BlockchainProperties;

public abstract class AbstractChainTask implements ChainTask {
	private BlockchainProperties.Chain chainData = null;

	protected final BlockchainProperties blockchainProperties;

	public AbstractChainTask(BlockchainProperties blockchainProperties) {
		this.blockchainProperties = blockchainProperties;
	}

	@Override
	public abstract void execute(Long forChain, Object parameters);

	protected BlockchainProperties.Chain getChainData(long chainId) {
		if (chainData == null) {
			var data = blockchainProperties.getChains().stream().filter(chain -> chain.getChainId() == chainId)
					.findFirst();

			if (data.isPresent()) {
				chainData = data.get();
			} else {
				throw new IllegalArgumentException("No chain configured for chainId: " + chainId);
			}
		}

		return chainData;
	}

	@SuppressWarnings("unchecked")
	protected <T> T getParameter(Object parameters, String fieldName) {
		if (Objects.isNull(parameters)) {
			throw new IllegalArgumentException("Called getParameter with a null parameters");
		}

		try {
			String getter = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
			try {
				return (T) parameters.getClass().getDeclaredMethod(getter).invoke(parameters);
			} catch (NoSuchMethodException e) {
				return (T) parameters.getClass().getDeclaredMethod(fieldName).invoke(parameters);
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalArgumentException(
					"No getter for '" + fieldName + "' on " + parameters.getClass().getSimpleName(), e);
		}
	}
}