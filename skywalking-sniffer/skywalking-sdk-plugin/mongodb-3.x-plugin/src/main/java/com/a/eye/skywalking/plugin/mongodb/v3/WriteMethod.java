package com.a.eye.skywalking.plugin.mongodb.v3;

/**
 * {@link WriteMethod} mongoDB write method enum
 *
 * @author baiyang
 */
public enum WriteMethod {
    DELETE("delete"), INSERT("insert"), UPDATE("update"), CREATECOLLECTION("createCollection"), CREATEINDEXES(
            "createIndexess"), CREATEVIEW("createView"), FINDANDDELETE("findAndDelete"), FINDANDREPLACE(
            "findAndReplace"), FINDANDUPDATE("findAndUpdate"), MAPREDUCETOCOLLECTION("mapReduceToCollection"),
    MIXEDBULKWRITE("mixedBulkWrite"), UNKNOW("unknow");

    private String name;

    private WriteMethod(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
