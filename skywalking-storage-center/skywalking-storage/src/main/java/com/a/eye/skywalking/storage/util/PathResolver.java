package com.a.eye.skywalking.storage.util;

import java.io.File;

public class PathResolver {

    private final static String STORAGE_HOME;

    static {
        STORAGE_HOME = System.getProperty("user.dir") + File.separator + "..";
    }

    public static String getAbsolutePath(String path) {
        if (path.charAt(0) != '/') {
            path = '/' + path;
        }
        return STORAGE_HOME + path;
    }

}
