package com.a.eye.skywalking.buffer;

import com.a.eye.skywalking.logging.LogManager;
import com.a.eye.skywalking.logging.Logger;
import com.a.eye.skywalking.protocol.common.ISerializable;

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



