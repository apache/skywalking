package com.a.eye.skywalking.storage.data.index;

/**
 * Created by xin on 2016/11/3.
 */
public class IndexMetaInfo {

    private String fileName;

    private long offset;

    private int length;

    public IndexMetaInfo(String name, long offset, int length) {
        this.fileName = name;
        this.offset = offset;
        this.length = length;
    }


    public String getFileName() {
        return fileName;
    }

    public long getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }
}
