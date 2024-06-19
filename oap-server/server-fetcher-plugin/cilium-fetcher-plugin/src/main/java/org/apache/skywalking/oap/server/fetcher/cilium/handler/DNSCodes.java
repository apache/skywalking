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

package org.apache.skywalking.oap.server.fetcher.cilium.handler;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class DNSCodes {

    // Follow https://www.iana.org/assignments/dns-parameters/dns-parameters.xhtml#dns-parameters-6
    public static final Map<Integer, String> RETURN_CODES = ImmutableMap.<Integer, String>builder()
        .put(0, "NoError")
        .put(1, "FormErr")
        .put(2, "ServFail")
        .put(3, "NXDomain")
        .put(4, "NotImp")
        .put(5, "Refused")
        .put(6, "YXDomain")
        .put(7, "YXRRSet")
        .put(8, "NXRRSet")
        .put(9, "NotAuth")
        .put(10, "NotZone")
        .put(11, "DSOTYPENI")
        .put(16, "BADVERS|BADSIG")
        .put(17, "BADKEY")
        .put(18, "BADTIME")
        .put(19, "BADMODE")
        .put(20, "BADNAME")
        .put(21, "BADALG")
        .put(22, "BADTRUNC")
        .put(23, "BADCOOKIE")
        .build();
}
