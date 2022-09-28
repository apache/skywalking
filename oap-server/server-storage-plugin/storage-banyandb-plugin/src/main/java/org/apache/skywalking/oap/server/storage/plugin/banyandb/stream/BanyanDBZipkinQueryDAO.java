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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.storage.query.IZipkinQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import zipkin2.Span;
import zipkin2.storage.QueryRequest;

//TODO: Not support BanyanDB for query yet.
public class BanyanDBZipkinQueryDAO extends AbstractBanyanDBDAO implements IZipkinQueryDAO {

    public BanyanDBZipkinQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<String> getServiceNames() throws IOException {
        return new ArrayList<>();
    }

    @Override
    public List<String> getRemoteServiceNames(final String serviceName) throws IOException {
        return new ArrayList<>();
    }

    @Override
    public List<String> getSpanNames(final String serviceName) throws IOException {
        return new ArrayList<>();
    }

    @Override
    public List<Span> getTrace(final String traceId) throws IOException {
        return new ArrayList<>();
    }

    @Override
    public List<List<Span>> getTraces(final QueryRequest request, Duration duration) throws IOException {
        return new ArrayList<>();
    }

    @Override
    public List<List<Span>> getTraces(final Set<String> traceIds) throws IOException {
        return new ArrayList<>();
    }
}
