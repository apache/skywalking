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

package org.apache.skywalking.oap.query.graphql.resolver;

import com.coxautodev.graphql.tools.GraphQLQueryResolver;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.oap.query.graphql.type.ClusterBrief;
import org.apache.skywalking.oap.query.graphql.type.Duration;
import org.apache.skywalking.oap.query.graphql.type.Endpoint;
import org.apache.skywalking.oap.query.graphql.type.Service;
import org.apache.skywalking.oap.query.graphql.type.ServiceInstance;

public class MetadataQuery implements GraphQLQueryResolver {
    public ClusterBrief getGlobalBrief(final Duration duration) {
        return new ClusterBrief();
    }

    public List<Service> getAllServices(final Duration duration) {
        return Collections.emptyList();
    }

    public List<Service> searchServices(final Duration duration, final String keyword) {
        return Collections.emptyList();
    }

    public List<ServiceInstance> getServiceInstances(final Duration duration, final String id) {
        return Collections.emptyList();
    }

    public List<Endpoint> searchEndpoint(final String keyword, final String serviceId, final int limit) {
        return Collections.emptyList();
    }
}
