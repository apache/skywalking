package com.ai.cloud.skywalking.analysis.chainbuild.exception;

public class TraceSpanTreeNotFountException extends Exception {
	private static final long serialVersionUID = 5559441397011866237L;

	public TraceSpanTreeNotFountException(String msg){
		super(msg);
	}
	
	public TraceSpanTreeNotFountException(String msg, Exception cause){
		super(msg, cause);
	}
}
