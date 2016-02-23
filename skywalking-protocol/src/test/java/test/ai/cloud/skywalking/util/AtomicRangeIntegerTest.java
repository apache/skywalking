package test.ai.cloud.skywalking.util;

import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;
import junit.framework.TestCase;

import com.ai.cloud.skywalking.util.AtomicRangeInteger;

public class AtomicRangeIntegerTest extends TestCase{
	public void testGet(){
		AtomicRangeInteger ari = new AtomicRangeInteger(0, 12);
		for(int i = 0; i < 51; i++){
			System.out.print(ari.getAndIncrement() + ";");
		}
	}
	
	public void testMultiThreads(){
		List<RangeIntegerThread> tlist = new ArrayList<RangeIntegerThread>();
		for(int i = 0; i < 20; i++){
			RangeIntegerThread t = new RangeIntegerThread();
			tlist.add(t);
			t.start();
		}
		
		for(int i = 0; i < tlist.size(); i++){
			try {
				tlist.get(i).join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
}

class RangeIntegerThread extends Thread{
	private static String[] buffer = new String[500000000];
	
	private static AtomicRangeInteger ari = new AtomicRangeInteger(0, buffer.length);
	
	@Override
	public void run(){
		while(true){
			int i = ari.getAndIncrement();
			if(i % 10000000 == 0){
				System.out.println(ari.get());
			}
			if(i >= buffer.length - 100000){
				break;
			}
			Assert.assertNull(buffer[i]);
			buffer[i] = "string";
		}
	}
}
