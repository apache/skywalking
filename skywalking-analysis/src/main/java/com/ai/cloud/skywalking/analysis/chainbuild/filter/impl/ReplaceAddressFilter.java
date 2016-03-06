package com.ai.cloud.skywalking.analysis.chainbuild.filter.impl;

import com.ai.cloud.skywalking.analysis.chainbuild.SpanEntry;
import com.ai.cloud.skywalking.analysis.chainbuild.filter.SpanNodeProcessFilter;
import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainNode;
import com.ai.cloud.skywalking.analysis.chainbuild.util.SubLevelSpanCostCounter;

public class ReplaceAddressFilter extends SpanNodeProcessFilter {

    //ip:port regex
    private static String IP_PORT_REGEX = "(([0-9]|[1-9][0-9]|1([0-9][0-9])|2([0-4][0-9]|5[0-5]))\\.([0-9]|[1-9][0-9]|1([0-9][0-9])|2([0-4][0-9]|5[0-5]))\\.([0-9]|[1-9][0-9]|1([0-9][0-9])|2([0-4][0-9]|5[0-5]))\\.([0-9]|[1-9][0-9]|1([0-9][0-9])|2([0-4][0-9]|5[0-5]))|localhost):([1-5][0-9]{4}|6[0-4][0-9]{3}|65[0-4][0-9]{2}|655[0-2][0-9]|6553[0-5]|[0-9]{1,4})";

    @Override
    public void doFilter(SpanEntry spanEntry, ChainNode node, SubLevelSpanCostCounter costMap) {

        String viewPoint = spanEntry.getViewPoint().replaceAll(IP_PORT_REGEX, spanEntry.getApplicationId());
        node.setViewPoint(viewPoint);

        this.doNext(spanEntry, node, costMap);
    }
}
