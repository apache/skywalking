package com.ai.cloud.skywalking.analysis.chainbuild.exception;

public class TraceSpanTreeSerializeException extends Exception {
	private static final long serialVersionUID = 7857716041262993579L;

	public TraceSpanTreeSerializeException(String msg){
		super(msg);
	}
	
	public TraceSpanTreeSerializeException(String msg, Exception cause){
		super(msg, cause);
	}
}
