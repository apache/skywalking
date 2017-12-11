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


package org.apache.skywalking.apm.plugin.grpc.v1.vo;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;

/**
 * {@link GRPCDynamicFields} contain the require information of span.
 *
 * @author zhangxin
 */
public class GRPCDynamicFields {
    private ServiceDescriptor descriptor;
    private Metadata metadata;
    private String authority;
    private ContextSnapshot snapshot;
    private int onNextCount;

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public String getAuthority() {
        return authority;
    }

    public void setAuthority(String authority) {
        this.authority = authority;
    }

    public String getRequestMethodName() {
        return descriptor.getServiceName();
    }

    public void setDescriptor(MethodDescriptor methodDescriptor) {
        this.descriptor = new ServiceDescriptor(methodDescriptor);
    }

    public void setDescriptor(ServiceDescriptor methodDescriptor) {
        this.descriptor = methodDescriptor;
    }

    public ServiceDescriptor getDescriptor() {
        return descriptor;
    }

    public ContextSnapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(ContextSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public MethodDescriptor.MethodType getMethodType() {
        return descriptor.getMethodType();
    }

    public void incrementOnNextCount() {
        onNextCount++;
    }

    public int getOnNextCount() {
        return onNextCount;
    }
}
