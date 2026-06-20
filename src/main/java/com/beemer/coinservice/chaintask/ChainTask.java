package com.beemer.coinservice.chaintask;

@FunctionalInterface
public interface ChainTask {
	void execute(Long forChain, Object parameters);
}
