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

    private ServerType[] serverTypes;

    private static final String HTTP = "http";
    private static final String GRPC = "gRPC";
    private static final String DUBBO = "dubbo";
    private static final String MOTAN = "motan";
    private static final String CLIENT = "client";
    private static final String JDBC_DRIVER = "JDBC driver";

    private ServerTypeDefine() {
        this.serverTypes = new ServerType[30];
        addServerType(new ServerType(ComponentsDefine.TOMCAT.getId(), Const.EMPTY_STRING));
        addServerType(new ServerType(ComponentsDefine.HTTPCLIENT.getId(), Const.EMPTY_STRING));
        addServerType(new ServerType(ComponentsDefine.DUBBO.getId(), DUBBO));
        addServerType(new ServerType(ComponentsDefine.H2.getId(), JDBC_DRIVER));
        addServerType(new ServerType(ComponentsDefine.MYSQL.getId(), JDBC_DRIVER));
        addServerType(new ServerType(ComponentsDefine.ORACLE.getId(), JDBC_DRIVER));
        addServerType(new ServerType(ComponentsDefine.REDIS.getId(), CLIENT));
        addServerType(new ServerType(ComponentsDefine.MOTAN.getId(), MOTAN));
        addServerType(new ServerType(ComponentsDefine.MONGODB.getId(), CLIENT));
        addServerType(new ServerType(ComponentsDefine.RESIN.getId(), Const.EMPTY_STRING));
        addServerType(new ServerType(ComponentsDefine.FEIGN.getId(), Const.EMPTY_STRING));
        addServerType(new ServerType(ComponentsDefine.OKHTTP.getId(), Const.EMPTY_STRING));
        addServerType(new ServerType(ComponentsDefine.SPRING_REST_TEMPLATE.getId(), Const.EMPTY_STRING));
        addServerType(new ServerType(ComponentsDefine.SPRING_MVC_ANNOTATION.getId(), Const.EMPTY_STRING));
        addServerType(new ServerType(ComponentsDefine.STRUTS2.getId(), Const.EMPTY_STRING));
        addServerType(new ServerType(ComponentsDefine.NUTZ_MVC_ANNOTATION.getId(), Const.EMPTY_STRING));
        addServerType(new ServerType(ComponentsDefine.NUTZ_HTTP.getId(), HTTP));
        addServerType(new ServerType(ComponentsDefine.JETTY_CLIENT.getId(), HTTP));
        addServerType(new ServerType(ComponentsDefine.JETTY_SERVER.getId(), Const.EMPTY_STRING));
        addServerType(new ServerType(ComponentsDefine.MEMCACHED.getId(), CLIENT));
        addServerType(new ServerType(ComponentsDefine.SHARDING_JDBC.getId(), JDBC_DRIVER));
        addServerType(new ServerType(ComponentsDefine.POSTGRESQL.getId(), JDBC_DRIVER));
        addServerType(new ServerType(ComponentsDefine.GRPC.getId(), GRPC));
        addServerType(new ServerType(ComponentsDefine.ELASTIC_JOB.getId(), Const.EMPTY_STRING));
        addServerType(new ServerType(ComponentsDefine.ROCKET_MQ.getId(), CLIENT));
        addServerType(new ServerType(ComponentsDefine.HTTP_ASYNC_CLIENT.getId(), HTTP));
        addServerType(new ServerType(ComponentsDefine.KAFKA.getId(), CLIENT));
        addServerType(new ServerType(ComponentsDefine.SERVICECOMB.getId(), Const.EMPTY_STRING));
        addServerType(new ServerType(ComponentsDefine.HYSTRIX.getId(), Const.EMPTY_STRING));
    }

    public static ServerTypeDefine getInstance() {
        return INSTANCE;
    }

    private void addServerType(ServerType serverType) {
        serverTypes[serverType.getId()] = serverType;
    }

    public ServerType getServerTypeByComponentId(int componentId) {
        return serverTypes[componentId];
    }

    public ServerType getServerType(int serverTypeId) {
        return serverTypes[serverTypeId];
    }
}
