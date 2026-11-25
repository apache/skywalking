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

import java.util.List;
import lombok.Getter;

/**
 * The BanyanDB API versions this client's code is written for. A BanyanDB server reports its API version through
 * {@code GetAPIVersion}, and several BanyanDB releases share one API version. Each constant names its version, then
 * the server API versions its code works with, its own included. Code that needs an API version runs only when
 * {@code version.isCompatibleWith(serverAPIVersion)} holds (see {@link APISince}).
 *
 * <p>Constants are declared oldest first. Code that runs without such a check is written for the {@link #oldest()}
 * version, so a server is supported only when the oldest version is compatible with it.
 */
@Getter
public enum BanyanDBAPIVersion {
    V0_11("0.11", "0.11", "0.13"),
    V0_13("0.13", "0.13");

    /**
     * The API version, as a server reports it.
     */
    private final String version;
    /**
     * The server API versions this version's code works with, its own version included.
     */
    private final List<String> compatibleServerVersions;

    BanyanDBAPIVersion(final String version, final String... compatibleServerVersions) {
        this.version = version;
        this.compatibleServerVersions = List.of(compatibleServerVersions);
    }

    /**
     * @param serverAPIVersion the API version a server reports, or null while it is not known, as
     *                         {@code BanyanDBClient#getServerAPIVersion()} is before the version is verified
     * @return true when code written for this API version works with that server; false for null
     */
    public boolean isCompatibleWith(final String serverAPIVersion) {
        return serverAPIVersion != null && compatibleServerVersions.contains(serverAPIVersion);
    }

    /**
     * @return the oldest API version, the one code without a version check is written for
     */
    public static BanyanDBAPIVersion oldest() {
        return values()[0];
    }
}
