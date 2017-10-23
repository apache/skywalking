/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking-ui
 */

package org.skywalking.apm.ui.creator;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * @author peng-yongsheng
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
