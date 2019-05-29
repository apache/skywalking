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

package org.apache.skywalking.apm.plugin.seata.enhanced;

import io.netty.buffer.ByteBuf;
import io.seata.core.protocol.transaction.BranchCommitRequest;
import io.seata.core.protocol.transaction.BranchRollbackRequest;

import java.util.HashMap;
import java.util.Map;

public class EnhancedBranchRollbackRequest extends BranchRollbackRequest implements EnhancedRequest {
    private Map<String, String> headers = new HashMap<String, String>();

    public EnhancedBranchRollbackRequest(final BranchRollbackRequest branchRollbackRequest) {
        setApplicationData(branchRollbackRequest.getApplicationData());
        setBranchType(branchRollbackRequest.getBranchType());
        setBranchId(branchRollbackRequest.getBranchId());
        setResourceId(branchRollbackRequest.getResourceId());
        setXid(branchRollbackRequest.getXid());
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(final Map<String, String> headers) {
        this.headers = headers;
    }

    @Override
    public void put(final String key, final String value) {
        headers.put(key, value);
    }

    @Override
    public String get(final String key) {
        return headers.get(key);
    }

    @Override
    public byte[] encode() {
        return EnhancedRequestHelper.encode(super.encode(), getHeaders());
    }

    @Override
    public boolean decode(final ByteBuf in) {
        if (!super.decode(in)) {
            return false;
        }
        EnhancedRequestHelper.decode(in, getHeaders());
        return true;
    }
}
