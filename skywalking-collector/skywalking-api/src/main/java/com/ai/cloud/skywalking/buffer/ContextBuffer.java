package com.ai.cloud.skywalking.buffer;

import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import com.ai.cloud.skywalking.protocol.common.ISerializable;

import java.util.concurrent.ThreadLocalRandom;


import static com.ai.cloud.skywalking.conf.Config.Buffer.POOL_SIZE;

public class ContextBuffer {
	private static Logger logger = LogManager.getLogger(ContextBuffer.class);

    private static BufferPool pool = new BufferPool();

    private ContextBuffer() {
        //non
    }

    public static void save(ISerializable data) {
    	try{
    		pool.save(data);
    	}catch(Throwable t){
    		logger.error("save span error.", t);
    	}
    }
}



