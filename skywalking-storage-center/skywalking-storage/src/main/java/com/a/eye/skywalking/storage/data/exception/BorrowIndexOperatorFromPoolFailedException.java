package com.a.eye.skywalking.storage.data.exception;

/**
 * Created by xin on 2016/11/21.
 */
public class BorrowIndexOperatorFromPoolFailedException extends RuntimeException {
    public BorrowIndexOperatorFromPoolFailedException(Exception parent){
        super(parent);
    }
}
