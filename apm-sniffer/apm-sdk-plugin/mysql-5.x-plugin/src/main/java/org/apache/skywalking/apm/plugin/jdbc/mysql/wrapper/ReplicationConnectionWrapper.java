/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.skywalking.apm.plugin.jdbc.mysql.wrapper;

import com.mysql.cj.api.jdbc.JdbcConnection;
import com.mysql.cj.api.jdbc.ha.ReplicationConnection;
import java.sql.SQLException;
import org.apache.skywalking.apm.plugin.jdbc.trace.ConnectionInfo;

public class ReplicationConnectionWrapper extends JdbcConnectionWrapper implements ReplicationConnection {

    public ReplicationConnectionWrapper(JdbcConnection delegate, ConnectionInfo connectionInfo) {
        super(delegate, connectionInfo);
    }

    @Override public long getConnectionGroupId() {
        return replicationConnection.getConnectionGroupId();
    }

    @Override public JdbcConnection getCurrentConnection() {
        return replicationConnection.getCurrentConnection();
    }

    @Override public JdbcConnection getMasterConnection() {
        return replicationConnection.getMasterConnection();
    }

    @Override public void promoteSlaveToMaster(String s) throws SQLException {
        replicationConnection.promoteSlaveToMaster(s);
    }

    @Override public void removeMasterHost(String s) throws SQLException {
        replicationConnection.removeMasterHost(s);
    }

    @Override public void removeMasterHost(String s, boolean b) throws SQLException {
        replicationConnection.removeMasterHost(s, b);
    }

    @Override public boolean isHostMaster(String s) {
        return replicationConnection.isHostMaster(s);
    }

    @Override public JdbcConnection getSlavesConnection() {
        return replicationConnection.getSlavesConnection();
    }

    @Override public void addSlaveHost(String s) throws SQLException {
        replicationConnection.addSlaveHost(s);
    }

    @Override public void removeSlave(String s) throws SQLException {
        replicationConnection.removeSlave(s);
    }

    @Override public void removeSlave(String s, boolean b) throws SQLException {
        replicationConnection.removeSlave(s, b);
    }

    @Override public boolean isHostSlave(String s) {
        return replicationConnection.isHostSlave(s);
    }

    private ReplicationConnection replicationConnection;
}
