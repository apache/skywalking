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

package org.apache.skywalking.apm.plugin.jdbc.connectionurl.parser;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.util.Iterator;
import java.util.ServiceLoader;
import org.apache.skywalking.apm.plugin.jdbc.trace.ConnectionInfo;

/**
 * {@link URLParser#parser(String)} support parse the connection url, such as Mysql, Oracle, H2 Database. But there are
 * some url cannot be parsed, such as Oracle connection url with multiple host.
 *
 * @author zhangxin
 */
public class URLParser {

    private static ServiceLoader<ConnectionURLParser> JDBCPARSERS
        = ServiceLoader.load(ConnectionURLParser.class, URLParser.class
        .getClassLoader());

    public static ConnectionInfo parser(String url, Connection conn) {
        ConnectionInfo rc = parser(url);
        return rc;
    }

    public static ConnectionInfo parser(String url) {
        Iterator<ConnectionURLParser> it = JDBCPARSERS.iterator();
        while (it.hasNext()) {
            ConnectionURLParser parser = (ConnectionURLParser)it.next();
            if (url.startsWith(parser.getJDBCURLPrefix())) {
                try {
                    Constructor<? extends ConnectionURLParser> rc = parser.getClass().getConstructor(String.class);
                    parser = rc.newInstance(url);
                    return parser.parse();
                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InstantiationException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        }
        // required special handling,
        throw new RuntimeException("no parser associate with " + url);
    }
}
