/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.skywalking.plugin.test.agent.tool.validator.assertor;

import java.util.List;

import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.exception.ParentSegmentNotFoundException;
import org.apache.skywalking.plugin.test.agent.tool.validator.entity.SegmentItem;

/**
 * Created by xin on 2017/7/16.
 */
public class ParentSegmentIdExpressParser {
    public static String parse(String express, List<SegmentItem> actual) {
        if (!express.trim().startsWith("${") && !express.trim().endsWith("}")) {
            return express;
        }

        String parentSegmentExpress = express.trim().substring(2, express.trim().length() - 1);

        int startIndexOfIndex = parentSegmentExpress.indexOf("[");
        String applicationCode = parentSegmentExpress.substring(0, startIndexOfIndex);
        int endIndexOfIndex = parentSegmentExpress.indexOf("]", startIndexOfIndex);
        int expectedSize = Integer.parseInt(parentSegmentExpress.substring(startIndexOfIndex + 1, endIndexOfIndex));
        for (SegmentItem segmentItem : actual) {
            if (segmentItem.applicationCode().equals(applicationCode)) {
                if (segmentItem.segments().size() <= expectedSize) {
                    throw new ParentSegmentNotFoundException(parentSegmentExpress);
                }

                return segmentItem.segments().get(expectedSize).segmentId();
            }
        }
        return express;
    }
}
