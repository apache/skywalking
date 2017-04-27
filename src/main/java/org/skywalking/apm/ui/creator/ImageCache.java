package org.skywalking.apm.ui.creator;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
@Component
public class ImageCache {

    public static final String UNDEFINED_IMAGE = "UNDEFINED";

    private Map<String, String> imageCache = new HashMap<>();

    public void putImage(String imageName, String base64Data) {
        imageCache.put(imageName.toLowerCase(), base64Data);
    }

    public String getImage(String imageName) {
        if (imageCache.containsKey(imageName.toLowerCase())) {
            return imageCache.get(imageName.toLowerCase());
        } else {
            return imageCache.get("UNDEFINED".toLowerCase());
        }
    }
}
