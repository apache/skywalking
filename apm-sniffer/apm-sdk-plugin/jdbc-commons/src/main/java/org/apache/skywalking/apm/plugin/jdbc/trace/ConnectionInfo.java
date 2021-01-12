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

package org.apache.skywalking.apm.plugin.jdbc.trace;

import org.apache.skywalking.apm.network.trace.component.OfficialComponent;

/**
 * {@link ConnectionInfo} stored the jdbc connection info, the connection info contains db type, host, port, database
 * name.
 */
public class ConnectionInfo {
    /**
     * DB type, such as mysql, oracle, h2.
     */
    private final String dbType;
    /**
     * Operation database name.
     */
    private String databaseName;

    private String databasePeer;

    /**
     * Component
     */
    private final OfficialComponent component;

    public ConnectionInfo(OfficialComponent component, String dbType, String host, int port, String databaseName) {
        this.dbType = dbType;
        this.databasePeer = host + ":" + port;
        this.databaseName = databaseName;
        this.component = component;
    }

    public ConnectionInfo(OfficialComponent component, String dbType, String hosts, String databaseName) {
        this.dbType = dbType;
        this.databasePeer = hosts;
        this.databaseName = databaseName;
        this.component = component;
    }

    public String getDBType() {
        return dbType;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getDatabasePeer() {
        return databasePeer;
    }

    public OfficialComponent getComponent() {
        return component;
    }

    public void setDatabaseName(String dataBaseName) {
        this.databaseName = dataBaseName;
    }
}
