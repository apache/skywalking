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

package org.apache.skywalking.oap.query.promql.rt;

import java.util.Map;
import org.apache.skywalking.oap.query.promql.rt.result.MatcherSetResult;
import org.apache.skywalking.oap.query.promql.rt.result.ParseResultType;
import org.apache.skywalking.promql.rt.grammar.PromQLParser;
import org.apache.skywalking.promql.rt.grammar.PromQLParserBaseVisitor;

public class PromQLMatchVisitor extends PromQLParserBaseVisitor<MatcherSetResult> {

    @Override
    public MatcherSetResult visitMetricInstant(PromQLParser.MetricInstantContext ctx) {
        String metricName = ctx.metricName().getText();
        MatcherSetResult result = new MatcherSetResult();
        result.setResultType(ParseResultType.MATCH);
        result.setMetricName(metricName);
        Map<String, String> labelMap = result.getLabelMap();
        if (ctx.labelList() != null) {
            for (PromQLParser.LabelContext labelCtx : ctx.labelList().label()) {
                String labelName = labelCtx.labelName().getText();
                String labelValue = labelCtx.labelValue().getText();
                String labelValueTrim = labelValue.substring(1, labelValue.length() - 1);
                labelMap.put(labelName, labelValueTrim);
            }
        }
        return result;
    }
}
