package com.ai.cloud.skywalking.reciever.storage;

import java.util.ArrayList;
import java.util.List;

import com.ai.cloud.skywalking.reciever.model.BuriedPointEntry;

public class Chain {
	private List<IStorageChain> chains = new ArrayList<IStorageChain>();
	
	private int index = 0;
	
	public void doChain(BuriedPointEntry entry, String entryOriginData ){
		if(index < chains.size()){
			chains.get(index++).doChain(entry, entryOriginData,  this);;
		}
	}
	
	synchronized void addChain(IStorageChain chain){
		chains.add(chain);
	}
}
