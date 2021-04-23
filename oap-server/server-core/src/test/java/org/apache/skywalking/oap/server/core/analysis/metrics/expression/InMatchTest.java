/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.server.core.analysis.metrics.expression;

import org.apache.skywalking.oap.server.core.source.RequestType;
import org.junit.Assert;
import org.junit.Test;

public class InMatchTest {

    @Test
    public void testIn() {
        Assert.assertTrue(new InMatch().match("a", new Object[] {
            "\"a\"",
            "\"b\""
        }));
        Assert.assertFalse(new InMatch().match("c", new Object[] {
            "\"a\"",
            "\"b\""
        }));

        Assert.assertTrue(
            new InMatch().match(RequestType.RPC, new Object[] {
                RequestType.HTTP,
                RequestType.DATABASE,
                RequestType.RPC
            }));
        Assert.assertFalse(
            new InMatch().match(RequestType.gRPC, new Object[] {
                                    RequestType.HTTP,
                                    RequestType.DATABASE,
                                    RequestType.RPC
                                }
            ));

        Assert.assertTrue(new InMatch().match(1, new long[] {
            1,
            2,
            3
        }));
        Assert.assertFalse(new InMatch().match(4, new long[] {
            1,
            2,
            3
        }));

        Assert.assertTrue(new InMatch().match(1L, new long[] {
            1,
            2,
            3
        }));
        Assert.assertFalse(new InMatch().match(4L, new long[] {
            1,
            2,
            3
        }));
    }
}
