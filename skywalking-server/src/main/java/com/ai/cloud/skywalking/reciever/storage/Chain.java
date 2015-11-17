package com.ai.cloud.skywalking.reciever.storage;

import java.util.List;

import com.ai.cloud.skywalking.reciever.model.BuriedPointEntry;

public class Chain {
	private List<IStorageChain> chains;
	
	private int index = 0;
	
	public Chain(List<IStorageChain> chains){
		this.chains = chains;
	}
	
	public void doChain(BuriedPointEntry entry, String entryOriginData ){
		if(index < chains.size()){
			chains.get(index++).doChain(entry, entryOriginData,  this);;
		}
	}
	
	void addChain(IStorageChain chain){
		chains.add(chain);
	}
}
