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

package org.apache.skywalking.oap.server.library.it;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ITVersions is a utility class to read the version information from the environment file.
 */
public class ITVersions {

    static Map<String, String> ENV = readingEnv("test", "e2e-v2", "script", "env");

    // The prefix of the integration version in the environment file.
    static final String INTERGRATION_PREFIX = "INTEGRATION_";

    // Get the version from the environment file.
    public static String get(String key) {
        // trying to find the integration version first
        final String integrationVal = ENV.get(INTERGRATION_PREFIX + key);
        if (integrationVal != null && integrationVal.length() > 0) {
            return integrationVal;
        }
        // if not found, return the normal version
        return ENV.get(key);
    }

    private static Map<String, String> readingEnv(String... envFile) {
        // Find the project root directory and get the environment file path.
        Path envFilePath = getProjectRootDir();
        for (String subPath : envFile) {
            envFilePath = envFilePath.resolve(subPath);
        }

        try {
            return Files.readAllLines(envFilePath).stream()
                .filter(l -> !l.startsWith("#"))
                .map(l -> l.split("=", 2))
                .filter(l -> l.length == 2)
                .collect(Collectors.toMap(l -> l[0], l -> l[1], (older, newer) -> newer));
        } catch (IOException e) {
            throw new IllegalStateException("Reading environment file error, path: " + envFile, e);
        }
    }

    // Get the project root directory which should contain the mvnw file.
    private static Path getProjectRootDir() {
        Path path = Paths.get(System.getProperty("user.dir"));
        while (!Files.exists(path.resolve("mvnw"))) {
            path = path.getParent();
        }
        return path;
    }

}
