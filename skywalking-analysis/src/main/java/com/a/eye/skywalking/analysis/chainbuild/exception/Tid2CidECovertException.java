package com.a.eye.skywalking.analysis.chainbuild.exception;

public class Tid2CidECovertException extends Exception{
	private static final long serialVersionUID = -4679233837335940374L;
	
	public Tid2CidECovertException(String msg){
		super(msg);
	}
	
	public Tid2CidECovertException(String msg, Exception cause){
		super(msg, cause);
	}

}
