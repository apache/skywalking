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

package org.apache.skywalking.oap.server.core.query.type;

import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.skywalking.oap.server.library.pprof.type.FrameTree;
import java.util.List;
import java.util.Objects;

@Setter
@Getter
@NoArgsConstructor
public class PprofStackTree {
    private List<PprofStackElement> elements;

    private int idGen = 0;

    public PprofStackTree(FrameTree tree) {
        this.elements = convertTree(-1, tree);
    }

    private List<PprofStackElement> convertTree(int parentId, FrameTree tree) {
        PprofStackElement pprofStackElement = new PprofStackElement();
        pprofStackElement.setId(idGen++);
        pprofStackElement.setParentId(parentId);
        pprofStackElement.setCodeSignature(tree.getSignature());
        pprofStackElement.setTotal(tree.getTotal());
        pprofStackElement.setSelf(tree.getSelf());

        List<FrameTree> children = tree.getChildren();
        List<PprofStackElement> result = Lists.newArrayList(pprofStackElement);
        if (Objects.isNull(children) || children.isEmpty()) {
            return result;
        }

        for (FrameTree child : children) {
            List<PprofStackElement> childElements = convertTree(pprofStackElement.getId(), child);
            result.addAll(childElements);
        }

        return result;
    }
}
