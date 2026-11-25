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

package org.apache.skywalking.library.banyandb.v1.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Holds the version lists to the rules the client relies on, whatever versions they name.
 */
public class BanyanDBAPIVersionTest {
    @Test
    public void everyVersionWorksWithServersOfItsOwnVersion() {
        for (final BanyanDBAPIVersion version : BanyanDBAPIVersion.values()) {
            assertTrue(version.isCompatibleWith(version.getVersion()), version.name());
        }
    }

    /**
     * A server the oldest version does not work with fails the boot, so no newer version may list it.
     */
    @Test
    public void everyCompatibleServerIsOneTheOldestVersionWorksWith() {
        final BanyanDBAPIVersion oldest = BanyanDBAPIVersion.oldest();
        for (final BanyanDBAPIVersion version : BanyanDBAPIVersion.values()) {
            for (final String server : version.getCompatibleServerVersions()) {
                assertTrue(oldest.isCompatibleWith(server), version.name() + " lists " + server);
            }
        }
    }

    @Test
    public void versionsAreDeclaredOldestFirst() {
        final BanyanDBAPIVersion[] versions = BanyanDBAPIVersion.values();
        assertEquals(versions[0], BanyanDBAPIVersion.oldest());
        for (int i = 1; i < versions.length; i++) {
            assertTrue(
                compare(versions[i - 1].getVersion(), versions[i].getVersion()) < 0,
                versions[i - 1].name() + " is declared before " + versions[i].name());
        }
    }

    /**
     * Code written for a version uses what that version added, which an older server does not have.
     */
    @Test
    public void noVersionWorksWithAnOlderServer() {
        for (final BanyanDBAPIVersion version : BanyanDBAPIVersion.values()) {
            for (final String server : version.getCompatibleServerVersions()) {
                assertTrue(compare(server, version.getVersion()) >= 0, version.name() + " lists " + server);
            }
        }
    }

    @Test
    public void unknownServerVersionIsNotCompatible() {
        for (final BanyanDBAPIVersion version : BanyanDBAPIVersion.values()) {
            assertFalse(version.isCompatibleWith("0.0"), version.name());
            assertFalse(version.isCompatibleWith(""), version.name());
            assertFalse(version.isCompatibleWith(null), version.name());
        }
    }

    private static int compare(final String left, final String right) {
        final String[] l = left.split("\\.");
        final String[] r = right.split("\\.");
        final int major = Integer.compare(Integer.parseInt(l[0]), Integer.parseInt(r[0]));
        return major != 0 ? major : Integer.compare(Integer.parseInt(l[1]), Integer.parseInt(r[1]));
    }
}
