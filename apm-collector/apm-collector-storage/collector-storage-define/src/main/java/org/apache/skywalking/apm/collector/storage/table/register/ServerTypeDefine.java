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

package org.apache.skywalking.apm.collector.storage.table.register;

import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * @author peng-yongsheng
 */
public class ServerTypeDefine {

    private static ServerTypeDefine INSTANCE = new ServerTypeDefine();

    private String[] serverTypeNames;
    private ServerType[] serverTypes;

    private ServerTypeDefine() {
        this.serverTypes = new ServerType[30];
        this.serverTypeNames = new String[11];
        addServerType(new ServerType(ComponentsDefine.TOMCAT.getId(), Const.NONE, Const.EMPTY_STRING));
        addServerType(new ServerType(ComponentsDefine.HTTPCLIENT.getId(), Const.NONE, Const.EMPTY_STRING));
        addServerType(new ServerType(ComponentsDefine.DUBBO.getId(), Const.NONE, Const.EMPTY_STRING));
        addServerType(new ServerType(ComponentsDefine.H2.getId(), 1, ComponentsDefine.H2.getName()));
        addServerType(new ServerType(ComponentsDefine.MYSQL.getId(), 2, ComponentsDefine.MYSQL.getName()));
        addServerType(new ServerType(ComponentsDefine.ORACLE.getId(), 3, ComponentsDefine.ORACLE.getName()));
        addServerType(new ServerType(ComponentsDefine.REDIS.getId(), 4, ComponentsDefine.REDIS.getName()));
        addServerType(new ServerType(ComponentsDefine.MOTAN.getId(), Const.NONE, Const.EMPTY_STRING));
        addServerType(new ServerType(ComponentsDefine.MONGODB.getId(), 5, ComponentsDefine.MONGODB.getName()));
        addServerType(new ServerType(ComponentsDefine.RESIN.getId(), Const.NONE, Const.EMPTY_STRING));
        addServerType(new ServerType(ComponentsDefine.FEIGN.getId(), Const.NONE, Const.EMPTY_STRING));
        addServerType(new ServerType(ComponentsDefine.OKHTTP.getId(), Const.NONE, Const.EMPTY_STRING));
        addServerType(new ServerType(ComponentsDefine.SPRING_REST_TEMPLATE.getId(), Const.NONE, Const.EMPTY_STRING));
        addServerType(new ServerType(ComponentsDefine.SPRING_MVC_ANNOTATION.getId(), Const.NONE, Const.EMPTY_STRING));
        addServerType(new ServerType(ComponentsDefine.STRUTS2.getId(), Const.NONE, Const.EMPTY_STRING));
        addServerType(new ServerType(ComponentsDefine.NUTZ_MVC_ANNOTATION.getId(), Const.NONE, Const.EMPTY_STRING));
        addServerType(new ServerType(ComponentsDefine.NUTZ_HTTP.getId(), Const.NONE, Const.EMPTY_STRING));
        addServerType(new ServerType(ComponentsDefine.JETTY_CLIENT.getId(), Const.NONE, Const.EMPTY_STRING));
        addServerType(new ServerType(ComponentsDefine.JETTY_SERVER.getId(), Const.NONE, Const.EMPTY_STRING));
        addServerType(new ServerType(ComponentsDefine.MEMCACHED.getId(), 6, ComponentsDefine.MEMCACHED.getName()));
        addServerType(new ServerType(ComponentsDefine.SHARDING_JDBC.getId(), 7, ComponentsDefine.SHARDING_JDBC.getName()));
        addServerType(new ServerType(ComponentsDefine.POSTGRESQL.getId(), 8, ComponentsDefine.POSTGRESQL.getName()));
        addServerType(new ServerType(ComponentsDefine.GRPC.getId(), Const.NONE, Const.EMPTY_STRING));
        addServerType(new ServerType(ComponentsDefine.ELASTIC_JOB.getId(), Const.NONE, Const.EMPTY_STRING));
        addServerType(new ServerType(ComponentsDefine.ROCKET_MQ.getId(), 9, ComponentsDefine.ROCKET_MQ.getName()));
        addServerType(new ServerType(ComponentsDefine.HTTP_ASYNC_CLIENT.getId(), Const.NONE, Const.EMPTY_STRING));
        addServerType(new ServerType(ComponentsDefine.KAFKA.getId(), 10, ComponentsDefine.KAFKA.getName()));
        addServerType(new ServerType(ComponentsDefine.SERVICECOMB.getId(), Const.NONE, ComponentsDefine.SERVICECOMB.getName()));
        addServerType(new ServerType(ComponentsDefine.HYSTRIX.getId(), Const.NONE, ComponentsDefine.HYSTRIX.getName()));
    }

    public static ServerTypeDefine getInstance() {
        return INSTANCE;
    }

    private void addServerType(ServerType serverType) {
        serverTypeNames[serverType.getId()] = serverType.getName();
        serverTypes[serverType.getComponentId()] = serverType;
    }

    public int getServerTypeId(int componentId) {
        return serverTypes[componentId].getId();
    }

    public String getServerType(int serverTypeId) {
        return serverTypeNames[serverTypeId];
    }
}
