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

package org.apache.skywalking.e2e.service;

import java.util.LinkedList;
import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@Data
@Slf4j
public class ServicesMatcher {
    private List<ServiceMatcher> services;

    public ServicesMatcher() {
        this.services = new LinkedList<>();
    }

    public void verify(final List<Service> services) {
        
        LOGGER.info("services:{} matchers:{}", services, this.getServices());
        assertThat(services).hasSameSizeAs(this.getServices());

        for (int i = 0; i < getServices().size(); i++) {
            boolean matched = false;
            for (Service service : services) {
                try {
                    this.getServices().get(i).verify(service);
                    matched = true;
                } catch (Throwable ignored) {
                }
            }
            if (!matched) {
                fail("\nExpected: %s\nActual: %s", getServices(), services);
            }
        }
    }

}
