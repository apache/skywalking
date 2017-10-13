package org.skywalking.apm.collector.core.util;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;

/**
 * @author pengys5
 */
public class ResourceUtils {

    public static Reader read(String fileName) throws FileNotFoundException {
        URL url = ResourceUtils.class.getClassLoader().getResource(fileName);
        if (url == null) {
            throw new FileNotFoundException("file not found: " + fileName);
        }
        InputStream inputStream = ResourceUtils.class.getClassLoader().getResourceAsStream(fileName);
        return new InputStreamReader(inputStream);
    }
}
