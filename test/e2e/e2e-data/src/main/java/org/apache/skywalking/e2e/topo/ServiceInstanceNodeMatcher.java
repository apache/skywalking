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

package org.apache.skywalking.e2e.topo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.skywalking.e2e.verification.AbstractMatcher;

import static java.util.Objects.nonNull;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class ServiceInstanceNodeMatcher extends AbstractMatcher<ServiceInstanceNode> {

    private String id;
    private String name;
    private String serviceId;
    private String serviceName;
    private String type;
    private String isReal;

    @Override
    public void verify(ServiceInstanceNode node) {
        if (nonNull(getId())) {
            final String expected = this.getId();
            final String actual = node.getId();

            doVerify(expected, actual);
        }

        if (nonNull(getName())) {
            final String expected = this.getName();
            final String actual = node.getName();

            doVerify(expected, actual);
        }

        if (nonNull(getServiceId())) {
            final String expected = this.getServiceId();
            final String actual = node.getServiceId();

            doVerify(expected, actual);
        }

        if (nonNull(getServiceName())) {
            final String expected = this.getServiceName();
            final String actual = node.getServiceName();

            doVerify(expected, actual);
        }

        if (nonNull(getType())) {
            final String expected = this.getType();
            final String actual = node.getType();

            doVerify(expected, actual);
        }

        if (nonNull(getIsReal())) {
            final String expected = this.getIsReal();
            final String actual = String.valueOf(node.getIsReal());

            doVerify(expected, actual);
        }
    }
}
