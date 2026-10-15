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
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceUtilsEncodedPathTest {
    private static final String DIRECTORY = "resource paths";
    private static final String FILE = DIRECTORY + "/config #1.txt";

    @Test
    void resolvesEncodedFilePath() throws Exception {
        final URL resource = ResourceUtils.class.getClassLoader().getResource(FILE);
        final Path expected = Path.of(resource.toURI());
        assertEquals(expected, ResourceUtils.getPath(FILE));
        assertTrue(Files.isRegularFile(ResourceUtils.getPath(FILE)));
    }

    @Test
    void listsFilesInEncodedDirectory() throws Exception {
        final File[] files = ResourceUtils.getPathFiles(DIRECTORY);
        assertEquals(1, files.length);
        assertEquals("config #1.txt", files[0].getName());
        assertTrue(files[0].isFile());
    }

    @Test
    void traversesEncodedDirectoryRecursively() throws Exception {
        final List<File> files = ResourceUtils.getDirectoryFilesRecursive(DIRECTORY, 1);
        assertEquals(2, files.size());
        assertTrue(files.stream().allMatch(File::isFile));
        assertTrue(files.stream().anyMatch(file -> file.getName().equals("config #1.txt")));
        assertTrue(files.stream().anyMatch(file -> file.getName().equals("nested.txt")));
        assertEquals(1, ResourceUtils.getDirectoryFilesRecursive(DIRECTORY, 0).size());
    }
}
