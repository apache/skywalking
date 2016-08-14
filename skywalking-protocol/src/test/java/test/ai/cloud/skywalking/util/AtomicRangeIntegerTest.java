package test.ai.cloud.skywalking.util;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import com.a.eye.skywalking.protocol.util.AtomicRangeInteger;

public class AtomicRangeIntegerTest extends TestCase{
	static String[] buffer = new String[5000];
	
	static AtomicRangeInteger ari = new AtomicRangeInteger(0, buffer.length);
	
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
	@Override
	public void run(){
		while(true){
			int i = AtomicRangeIntegerTest.ari.getAndIncrement();
			if(i % 10000000 == 0){
				System.out.println(AtomicRangeIntegerTest.ari.get());
			}
			if(AtomicRangeIntegerTest.buffer[i] != null){
				System.out.println("end at index:" + i + "," + AtomicRangeIntegerTest.buffer[i]);
				break;
			}else{
				System.out.println("at index:" + i);
				AtomicRangeIntegerTest.buffer[i] = "string";
			}
		}
	}
}
