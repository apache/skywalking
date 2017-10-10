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
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.plugin.jdbc.connectionurl.parser;

public abstract class AbstractURLParser implements ConnectionURLParser {

    protected String url;

    public AbstractURLParser(String url) {
        this.url = url;
    }

    /**
     * Fetch the index range that database host and port from connection url.
     *
     * @return index range that database hosts.
     */
    protected abstract int[] fetchDatabaseHostsIndexRange();

    /**
     * Fetch the index range that database name from connection url.
     *
     * @return index range that database name.
     */
    protected abstract int[] fetchDatabaseNameIndexRange();

    /**
     * Fetch database host(s) from connection url.
     *
     * @return database host(s).
     */
    protected String fetchDatabaseHostsFromURL() {
        int[] indexRange = fetchDatabaseHostsIndexRange();
        return url.substring(indexRange[0], indexRange[1]);
    }

    /**
     * Fetch database name from connection url.
     *
     * @return database name.
     */
    protected String fetchDatabaseNameFromURL() {
        int[] indexRange = fetchDatabaseNameIndexRange();
        return url.substring(indexRange[0], indexRange[1]);
    }

    /**
     * Fetch database name from connection url.
     *
     * @return database name.
     */
    protected String fetchDatabaseNameFromURL(int[] indexRange) {
        return url.substring(indexRange[0], indexRange[1]);
    }

}
