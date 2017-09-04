package org.skywalking.apm.collector.core.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.net.URL;

/**
 * @author pengys5
 */
public class ResourceUtils {

    public static FileReader read(String fileName) throws FileNotFoundException {
        URL url = ResourceUtils.class.getClassLoader().getResource(fileName);
        if (url == null) {
            throw new FileNotFoundException("file not found: " + fileName);
        }
        File file = new File(ResourceUtils.class.getClassLoader().getResource(fileName).getFile());
        return new FileReader(file);
    }
}
