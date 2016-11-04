package com.a.eye.skywalking.storage.data.file;

public class DataFile {

    private static final long MAX_LENGTH = 3 * 1024 * 1024 * 1024;

    private String name;
    private long   currentLength;

    public boolean overLimitLength() {
        return currentLength > MAX_LENGTH;
    }


    public long writeAndFlush(byte[] data) {

        return 0;
    }

    public String getName() {
        return name;
    }

    public void close() {

    }
}
