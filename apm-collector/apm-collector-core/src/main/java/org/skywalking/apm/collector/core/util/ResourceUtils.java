package org.skywalking.apm.collector.core.util;

import java.io.FileNotFoundException;
import java.io.FileReader;

/**
 * @author pengys5
 */
public class ResourceUtils {

    private static final String PATH = ResourceUtils.class.getResource("/").getPath();

    public static FileReader read(String fileName) throws FileNotFoundException {
        return new FileReader(PATH + fileName);
    }
}
