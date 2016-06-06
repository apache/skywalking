package com.ai.cloud.skywalking.buffer;

import com.ai.cloud.skywalking.protocol.Span;

import java.util.concurrent.ThreadLocalRandom;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.ai.cloud.skywalking.conf.Config.Buffer.POOL_SIZE;

public class ContextBuffer {
	private static Logger logger = LogManager.getLogger(ContextBuffer.class);

    private static BufferPool pool = new BufferPool();

    private ContextBuffer() {
        //non
    }

    public static void save(Span span) {
    	try{
    		pool.save(span);
    	}catch(Throwable t){
    		logger.error("save span error.", t);
    	}
    }


    static class BufferPool {
        // 注意： 这个变量名如果改变需要改变test-api工程中的Config变量
        private static BufferGroup[] bufferGroups = new BufferGroup[POOL_SIZE];
        static {
            for (int i = 0; i < POOL_SIZE; i++) {
                bufferGroups[i] = new BufferGroup("buffer_group-" + i);
            }
        }

        public void save(Span span) {
            bufferGroups[ThreadLocalRandom.current().nextInt(0, POOL_SIZE)].save(span);
        }

    }
}



