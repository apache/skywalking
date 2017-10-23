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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Base64;
import javax.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author peng-yongsheng
 */
@Component
public class ImageBase64Creator {

    private Logger logger = LogManager.getFormatterLogger(ImageBase64Creator.class);

    private final String ImageFolder_Relative_PATH = File.separator + "public" + File.separator + "img" + File.separator +
        "node" + File.separator;

    private final String PNG_BASE64_PREFIX = "data:image/png;base64,";
    private final String JPG_BASE64_PREFIX = "data:image/png;base64,";

    private final String PNG = "png";
    private final String JPG = "jpg";

    @Autowired
    private ImageCache imageCache;

    @PostConstruct
    public void loadImage() {
        String imageFolder = getImageFolder();

        String[] imageFileList = readImageFileList(imageFolder);
        for (String nodeImageFile : imageFileList) {
            logger.debug("nodeImageFile: %s", nodeImageFile);

            byte[] imageData = imageRead(new File(imageFolder, nodeImageFile));
            if (nodeImageFile.toLowerCase().endsWith(PNG)) {
                String encodeImage = imageEncode(imageData, PNG);
                String imageName = getImageName(nodeImageFile);
                imageCache.putImage(imageName, encodeImage);
            } else if (nodeImageFile.toLowerCase().endsWith(JPG)) {
                String encodeImage = imageEncode(imageData, JPG);
                imageCache.putImage(getImageName(nodeImageFile), encodeImage);
            } else {
                logger.error("ignore unsupported image type, image file name: %s", nodeImageFile);
            }
        }
    }

    private String getImageName(String nodeImageFile) {
        return nodeImageFile.split("\\.")[0];
    }

    private String getImageFolder() {
        URL url = this.getClass().getResource("/");
        logger.debug("root class path: %s", url.getPath());
        String imageFolder = new File(url.getPath(), ImageFolder_Relative_PATH).getAbsolutePath();
        return imageFolder;
    }

    private String[] readImageFileList(String imageFolder) {
        File file = new File(imageFolder);
        if (file.isDirectory()) {
            return file.list();
        } else {
            throw new IllegalArgumentException("image folder path error: " + imageFolder);
        }
    }

    private byte[] imageRead(File imgFile) {
        InputStream inputStream;
        byte[] imageData = null;
        try {
            inputStream = new FileInputStream(imgFile);
            imageData = new byte[inputStream.available()];
            inputStream.read(imageData);
            inputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return imageData;
    }

    private String imageEncode(byte[] imageData, String imageType) {
        if (PNG.equals(imageType)) {
            return PNG_BASE64_PREFIX + new String(Base64.getEncoder().encode(imageData));
        } else {
            return JPG_BASE64_PREFIX + new String(Base64.getEncoder().encode(imageData));
        }
    }
}
