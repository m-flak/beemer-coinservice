package com.beemer.coinservice.chaintask.exception;

import org.jspecify.annotations.Nullable;
import org.springframework.core.NestedRuntimeException;

public class ChainTaskFailureException extends NestedRuntimeException {

	private static final long serialVersionUID = 4829105473619205834L;

	private final String failureStage;

	public ChainTaskFailureException(String msg) {
		super(msg);
		failureStage = "";
	}

	public ChainTaskFailureException(String msg, @Nullable Throwable ex) {
		super(msg, ex);
		failureStage = "";
	}

	public ChainTaskFailureException(String msg, String failureStage, @Nullable Throwable ex) {
		super(msg, ex);
		this.failureStage = failureStage;
	}

	/**
	 * Return the stage in the flow that the failure happened. This correlates
	 * (should) to the last TRACE statement.
	 */
	public String getFailureStage() {
		return failureStage;
	}
}