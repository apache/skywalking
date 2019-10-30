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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.exception.ActualSegmentItemEmptyException;
import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.exception.SegmentItemNotFoundException;
import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.exception.SegmentSizeNotEqualsException;
import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.exception.ValueAssertFailedException;
import org.apache.skywalking.plugin.test.agent.tool.validator.entity.Segment;
import org.apache.skywalking.plugin.test.agent.tool.validator.entity.SegmentItem;
import org.apache.skywalking.plugin.test.agent.tool.validator.entity.SegmentRef;
import org.apache.skywalking.plugin.test.agent.tool.validator.entity.Span;

/**
 * Created by xin on 2017/7/15.
 */
public class SegmentItemsAssert {
    private static Logger logger = LogManager.getLogger(SegmentItemsAssert.class);

    public static void assertEquals(List<SegmentItem> expected, List<SegmentItem> actual) {
        if (expected == null) {
            logger.info("ignore segment items. because expected segment item is null.");
            return;
        }

        for (SegmentItem item : expected) {
            SegmentItem actualSegmentItem = findSegmentItem(actual, item);
            try {
                assertSegmentSize(item.segmentSize(), actualSegmentItem.segmentSize());
            } catch (ValueAssertFailedException e) {
                throw new SegmentSizeNotEqualsException(item.applicationCode(), item.segmentSize(), actualSegmentItem.segmentSize());
            }
            SegmentAssert.assertEquals(item, actualSegmentItem);
        }

        for (SegmentItem item : expected) {
            if (item.segments() == null) {
                continue;
            }

            for (Segment segment : item.segments()) {
                convertParentSegmentId(segment, expected);

                for (Span span : segment.spans()) {
                    if (span.refs() == null || span.refs().size() == 0) {
                        continue;
                    }
                    SegmentRefAssert.assertEquals(span.refs(), span.actualRefs());
                }
            }
        }
    }

    private static void convertParentSegmentId(Segment segment, List<SegmentItem> actual) {
        for (Span span : segment.spans()) {
            if (span.refs() == null || span.refs().size() == 0) {
                continue;
            }

            for (SegmentRef ref : span.refs()) {
                String actualParentSegmentId = ParentSegmentIdExpressParser.parse(ref.parentTraceSegmentId(), actual);
                ref.parentTraceSegmentId(actualParentSegmentId);
            }
        }
    }

    private static void assertSegmentSize(String expected, String actual) {
        if (expected == null) {
            return;
        }
        ExpressParser.parse(expected).assertValue("segment size", actual);
    }

    private static SegmentItem findSegmentItem(List<SegmentItem> actual, SegmentItem expected) {
        if (actual == null) {
            throw new ActualSegmentItemEmptyException(expected);
        }

        for (SegmentItem segmentItem : actual) {
            if (expected.applicationCode().equals(segmentItem.applicationCode())) {
                return segmentItem;
            }
        }

        throw new SegmentItemNotFoundException(expected.applicationCode());
    }
}
