/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.library.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResourceUtils {

    private static final String CONFIG_PATH_ENV = "SW_CONFIG_PATHS";

    private static Path PATH = null;

    static {
        if (System.getenv(CONFIG_PATH_ENV) != null) {
            String skywalkingConfigPaths = System.getenv(CONFIG_PATH_ENV);
            PATH = Paths.get(skywalkingConfigPaths);
        }
    }

    public static Reader read(String fileName) throws IOException {
        return new InputStreamReader(readToStream(fileName), StandardCharsets.UTF_8);
    }

    public static InputStream readToStream(String fileName) throws IOException {
        return Files.newInputStream(PATH.resolve(fileName));
    }

    public static File[] getPathFiles(String path) throws IOException {
        return getDirectoryFilesRecursive(path, 1).toArray(new File[0]);
    }

    /**
     * @param directoryPath the directory path
     * @param maxDepth      the max directory depth to get the files, the given directory is 0 as the tree root
     * @return all normal files which in this directory and subDirectory according to the maxDepth
     * @throws FileNotFoundException the directory not exist in the given path
     */
    public static List<File> getDirectoryFilesRecursive(String directoryPath,
                                                        int maxDepth) throws IOException {
        Path subPath = PATH.resolve(directoryPath);
        List<File> fileList = new ArrayList<>();
        Files.walk(subPath, maxDepth)
                .filter(Files::isRegularFile)
                .forEach(p -> fileList.add(p.toFile()));
        return fileList;
    }

    public static Path getPath(String path) {
        return PATH.resolve(path);
    }

}
