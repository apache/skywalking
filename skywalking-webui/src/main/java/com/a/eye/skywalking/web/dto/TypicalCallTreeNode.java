package com.a.eye.skywalking.web.dto;

import com.a.eye.skywalking.web.util.StringUtil;
import com.a.eye.skywalking.web.util.TokenGenerator;

/**
 * Created by xin on 16-4-28.
 */
public class TypicalCallTreeNode {
    private String nodeToken;
    private String viewPoint;
    private String viewPointStr;
    private String traceLevelId;

    public TypicalCallTreeNode(String parentLevelId, String levelId, String viewPoint) {
        if (StringUtil.isBlank(parentLevelId)){
            this.traceLevelId = levelId;
        }else{
            this.traceLevelId = parentLevelId + "." + levelId;
        }

        this.viewPoint = viewPoint;
        this.viewPointStr = viewPoint;
        nodeToken = TokenGenerator.generate(this.traceLevelId + ":" + viewPoint);
    }

    public String getNodeToken() {
        return nodeToken;
    }

    public void beautifulViewPoint() {
        if (!StringUtil.isBlank(viewPoint) && viewPoint.length() > 80) {
            viewPoint = viewPoint.substring(0, 50) + "..."
                    + viewPoint.substring(viewPoint.length() - 50);
        }
    }
}
