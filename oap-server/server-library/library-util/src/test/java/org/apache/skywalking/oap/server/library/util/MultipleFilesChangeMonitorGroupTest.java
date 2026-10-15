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

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MultipleFilesChangeMonitorGroupTest {
    @TempDir
    Path directory;

    @Test
    void loadsEveryChangedFileBeforeNotifying() throws Exception {
        final Path first = directory.resolve("cert");
        final Path second = directory.resolve("key");
        Files.write(first, bytes("cert-1"));
        Files.write(second, bytes("key-1"));
        final List<List<byte[]>> notifications = new ArrayList<>();
        final MultipleFilesChangeMonitor monitor = new MultipleFilesChangeMonitor(
            0, notifications::add, first.toString(), second.toString());

        check(monitor);
        assertEquals(1, notifications.size());
        assertArrayEquals(bytes("cert-1"), notifications.get(0).get(0));
        assertArrayEquals(bytes("key-1"), notifications.get(0).get(1));

        final long modified = Math.max(Files.getLastModifiedTime(first).toMillis(),
                                       Files.getLastModifiedTime(second).toMillis()) + 2000;
        Files.write(first, bytes("cert-2"));
        Files.write(second, bytes("key-2"));
        Files.setLastModifiedTime(first, FileTime.fromMillis(modified));
        Files.setLastModifiedTime(second, FileTime.fromMillis(modified));

        check(monitor);
        assertEquals(2, notifications.size());
        assertArrayEquals(bytes("cert-2"), notifications.get(1).get(0));
        assertArrayEquals(bytes("key-2"), notifications.get(1).get(1));
        check(monitor);
        assertEquals(2, notifications.size());
    }

    @Test
    void clearsDeletedContentAndDetectsIdenticalRecreation() throws Exception {
        final Path file = directory.resolve("config");
        Files.write(file, bytes("config"));
        final List<List<byte[]>> notifications = new ArrayList<>();
        final MultipleFilesChangeMonitor monitor = new MultipleFilesChangeMonitor(
            0, notifications::add, file.toString());

        check(monitor);
        assertEquals(1, notifications.size());
        Files.delete(file);
        check(monitor);
        assertEquals(2, notifications.size());
        assertNull(notifications.get(1).get(0));
        check(monitor);
        assertEquals(2, notifications.size());
        Files.write(file, bytes("config"));
        check(monitor);
        assertEquals(3, notifications.size());
        assertArrayEquals(bytes("config"), notifications.get(2).get(0));
    }

    private static byte[] bytes(final String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static void check(final MultipleFilesChangeMonitor monitor) throws Exception {
        final Method method = MultipleFilesChangeMonitor.class.getDeclaredMethod("checkAndNotify");
        method.setAccessible(true);
        method.invoke(monitor);
    }
}
