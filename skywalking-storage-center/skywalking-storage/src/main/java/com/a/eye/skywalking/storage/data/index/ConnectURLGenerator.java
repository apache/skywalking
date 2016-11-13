package com.a.eye.skywalking.storage.data.index;

/**
 * Created by xin on 2016/11/13.
 */
public class ConnectURLGenerator {

    private String basePath;
    private String dbFileName;

    public ConnectURLGenerator(String basePath, String dbFileName) {
        this.basePath = basePath;
        this.dbFileName = dbFileName;
    }


    public String generate(long timestamp) {
        return "jdbc:hsqldb:file:" + basePath + "/" + timestamp + "/" + dbFileName;
    }
}
