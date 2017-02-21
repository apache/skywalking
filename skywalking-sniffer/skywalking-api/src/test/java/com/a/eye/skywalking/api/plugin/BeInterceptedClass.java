package com.a.eye.skywalking.api.plugin;

public class BeInterceptedClass {
	public BeInterceptedClass(){
		System.out.println("BeInterceptedClass constructor.");
	}
	
	public void printabc(){
		System.out.println("printabc");
	}
	
	public static void call(){
		System.out.println("static call");
	}
}
