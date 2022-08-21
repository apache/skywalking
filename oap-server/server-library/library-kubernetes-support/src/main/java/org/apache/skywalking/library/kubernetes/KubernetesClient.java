/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.skywalking.library.kubernetes;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.util.Config;
import lombok.experimental.UtilityClass;

@UtilityClass
public class KubernetesClient {
    public void setDefault() throws IOException {
        final ApiClient apiClient = Config.defaultClient();
        apiClient
            .setHttpClient(apiClient.getHttpClient()
            .newBuilder()
            .readTimeout(0, TimeUnit.SECONDS)
            .build());
        Configuration.setDefaultApiClient(apiClient);
    }
}
