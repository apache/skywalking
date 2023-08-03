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

package org.apache.skywalking.oap.query.logql.rt;

import java.util.Map;
import java.util.Objects;
import org.apache.skywalking.logql.rt.grammar.LogQLParser;
import org.apache.skywalking.logql.rt.grammar.LogQLParserBaseVisitor;
import org.apache.skywalking.oap.query.logql.rt.result.LogQLParseResult;
import org.apache.skywalking.oap.server.library.util.StringUtil;

public class LogQLExprVisitor extends LogQLParserBaseVisitor<LogQLParseResult> {

    private static final String ALL_VALUE = "*";

    @Override
    public LogQLParseResult visitRoot(final LogQLParser.RootContext ctx) {
        LogQLParseResult result = visit(ctx.streamSelector());
        if (ctx.lineFilterList() != null) {
            LogQLParseResult filterResult = visit(ctx.lineFilterList());
            result.setKeywordsOfContent(filterResult.getKeywordsOfContent());
            result.setExcludingKeywordsOfContent(filterResult.getExcludingKeywordsOfContent());
        }
        return result;
    }

    @Override
    public LogQLParseResult visitStreamSelector(final LogQLParser.StreamSelectorContext ctx) {
        LogQLParseResult result = new LogQLParseResult();
        Map<String, String> labelMap = result.getLabelMap();
        if (ctx.labelList() != null) {
            for (LogQLParser.LabelContext labelCtx : ctx.labelList().label()) {
                String labelName = labelCtx.labelName().getText();
                String labelValue = labelCtx.labelValue().getText();
                String labelValueTrim = labelValue.substring(1, labelValue.length() - 1);
                // filter blank value or * to support service_instance & endpoint All query in Grafana
                if (StringUtil.isBlank(labelValueTrim) || Objects.equals(ALL_VALUE, labelValueTrim)) {
                    continue;
                }

                labelMap.put(labelName, labelValueTrim);
            }
        }
        return result;
    }

    @Override
    public LogQLParseResult visitLineFilterList(final LogQLParser.LineFilterListContext ctx) {
        LogQLParseResult filterResult = new LogQLParseResult();
        for (final LogQLParser.LineFilterContext lineFilterContext : ctx.lineFilter()) {
            String filterValue = lineFilterContext.filterValue().getText();
            String filterValueTrim = filterValue.substring(1, filterValue.length() - 1);
            if (StringUtil.isEmpty(filterValueTrim)) {
                continue;
            }

            if (lineFilterContext.operator().getStart().getType() == LogQLParser.CONTAINS) {
                filterResult.getKeywordsOfContent().add(filterValueTrim);
            }
            if (lineFilterContext.operator().getStart().getType() == LogQLParser.NOT_CONTAINS) {
                filterResult.getExcludingKeywordsOfContent().add(filterValueTrim);
            }
        }
        return filterResult;
    }
}
