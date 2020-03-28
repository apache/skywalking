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

package org.apache.skywalking.oap.server.core.analysis.manual;

import org.junit.Assert;
import org.junit.Test;

public class RelationDefineUtilTest {
    @Test
    public void testRelationID() {
        RelationDefineUtil.RelationDefine define = new RelationDefineUtil.RelationDefine(1, 2, 0);
        final RelationDefineUtil.RelationDefine relationDefine = RelationDefineUtil.splitEntityId(
            RelationDefineUtil.buildEntityId(define));
        Assert.assertEquals(define, relationDefine);
    }

    @Test
    public void testEndpointRelationID() {
        final RelationDefineUtil.EndpointRelationDefine endpointRelationDefine = new RelationDefineUtil.EndpointRelationDefine(
            1, "/source/endpoint",
            2, "/dest/endpoint",
            0
        );
        final RelationDefineUtil.EndpointRelationDefine anotherRelation = RelationDefineUtil.splitEndpointRelationEntityId(
            RelationDefineUtil.buildEndpointRelationEntityId(endpointRelationDefine));
        Assert.assertEquals(endpointRelationDefine, anotherRelation);

    }
}
