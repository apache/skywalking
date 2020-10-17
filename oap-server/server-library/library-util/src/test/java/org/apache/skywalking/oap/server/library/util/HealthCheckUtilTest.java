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

package org.apache.skywalking.oap.server.library.util;

import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Test;

import java.util.Set;

import static org.hamcrest.CoreMatchers.is;

public class HealthCheckUtilTest {

    @Test
    public void hasUnHealthAddressFalse() {
        Set<String> address = Sets.newHashSet("123.23.4.2");
        boolean flag = HealthCheckUtil.hasUnHealthAddress(address);
        Assert.assertThat(flag, is(false));
    }

    @Test
    public void hasUnHealthAddressWithNull() {
        Set<String> address = null;
        boolean flag = HealthCheckUtil.hasUnHealthAddress(address);
        Assert.assertThat(flag, is(false));
    }

    @Test
    public void hasUnHealthAddressWithEmptySet() {
        Set<String> address = Sets.newHashSet();
        boolean flag = HealthCheckUtil.hasUnHealthAddress(address);
        Assert.assertThat(flag, is(false));
    }

    @Test
    public void hasUnHealthAddressTrue() {
        Set<String> address = Sets.newHashSet("123.23.4.2", "127.0.0.1");
        boolean flag = HealthCheckUtil.hasUnHealthAddress(address);
        Assert.assertThat(flag, is(true));
    }
}
