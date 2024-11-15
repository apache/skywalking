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

package org.apache.skywalking.oap.server.core.query.type;

import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.skywalking.oap.server.library.jfr.type.FrameTree;
import org.apache.skywalking.oap.server.library.jfr.type.JFREventType;

import java.util.List;
import java.util.Objects;

@Setter
@Getter
@NoArgsConstructor
public class AsyncProfilerStackTree {
    private JFREventType type;
    private List<AsyncProfilerStackElement> elements;

    private int idGen = 0;

    public AsyncProfilerStackTree(JFREventType type, FrameTree tree) {
        this.type = type;
        this.elements = convertTree(-1, tree);
    }

    private List<AsyncProfilerStackElement> convertTree(int parentId, FrameTree tree) {
        AsyncProfilerStackElement asyncProfilerStackElement = new AsyncProfilerStackElement();
        asyncProfilerStackElement.setId(idGen++);
        asyncProfilerStackElement.setParentId(parentId);
        asyncProfilerStackElement.setCodeSignature(tree.getFrame());
        asyncProfilerStackElement.setTotal(tree.getTotal());
        asyncProfilerStackElement.setSelf(tree.getSelf());

        List<FrameTree> children = tree.getChildren();
        List<AsyncProfilerStackElement> result = Lists.newArrayList(asyncProfilerStackElement);
        if (Objects.isNull(children) || children.isEmpty()) {
            return result;
        }

        for (FrameTree child : children) {
            List<AsyncProfilerStackElement> childElements = convertTree(asyncProfilerStackElement.getId(), child);
            result.addAll(childElements);
        }

        return result;
    }
}
