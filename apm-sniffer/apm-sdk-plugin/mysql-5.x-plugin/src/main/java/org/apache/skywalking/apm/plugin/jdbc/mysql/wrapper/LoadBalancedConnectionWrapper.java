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

import com.mysql.cj.api.jdbc.ha.LoadBalancedConnection;
import java.sql.SQLException;
import org.apache.skywalking.apm.plugin.jdbc.trace.ConnectionInfo;

public class LoadBalancedConnectionWrapper extends JdbcConnectionWrapper implements LoadBalancedConnection {

    @Override
    public boolean addHost(String s) throws SQLException {
        return delegate.addHost(s);
    }

    @Override public void removeHost(String s) throws SQLException {
        delegate.removeHost(s);
    }

    @Override public void removeHostWhenNotInUse(String s) throws SQLException {
        delegate.removeHostWhenNotInUse(s);
    }

    @Override public void ping(boolean b) throws SQLException {
        delegate.ping(b);
    }

    private LoadBalancedConnection delegate;

    public LoadBalancedConnectionWrapper(LoadBalancedConnection delegate, ConnectionInfo info) {
        super(delegate, info);
    }
}
