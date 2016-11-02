package com.a.eye.skywalking.storage.block.index.exception;

/**
 * Created by xin on 2016/11/2.
 */
public class BlockIndexPersistenceFailedException extends Exception {
    public BlockIndexPersistenceFailedException(String message, Exception e) {
        super(message, e);
    }
}
