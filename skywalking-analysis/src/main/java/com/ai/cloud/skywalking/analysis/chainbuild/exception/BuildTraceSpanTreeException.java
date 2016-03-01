package com.ai.cloud.skywalking.analysis.chainbuild.exception;

public class BuildTraceSpanTreeException extends Exception {
	private static final long serialVersionUID = 5816399370389190974L;

	public BuildTraceSpanTreeException(String msg){
		super(msg);
	}
	
	public BuildTraceSpanTreeException(String msg, Exception cause){
		super(msg, cause);
	}
}
