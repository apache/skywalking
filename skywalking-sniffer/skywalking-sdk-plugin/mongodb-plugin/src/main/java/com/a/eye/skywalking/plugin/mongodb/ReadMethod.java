package com.a.eye.skywalking.plugin.mongodb;

/**
 * {@link ReadMethod} mongoDB read method enum
 *
 * @author baiyang
 */
public enum ReadMethod {
    COUNT("count"), DISTINCT("distinct"), FIND("find"), GROUP("group"), LIST_COLLECTIONS("listCollections"),
    MAPREDUCE_WITHINLINE_RESULTS("mapReduceWithInlineResults");

    private String name;

    private ReadMethod(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}
