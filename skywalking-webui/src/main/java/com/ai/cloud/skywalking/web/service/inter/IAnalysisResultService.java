package com.ai.cloud.skywalking.web.service.inter;

import com.ai.cloud.skywalking.web.dto.CallChainTree;
import com.ai.cloud.skywalking.web.dto.TypicalCallTree;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;

/**
 * Created by xin on 16-4-25.
 */
public interface IAnalysisResultService {
    CallChainTree fetchAnalysisResult(String treeId, String analyType, String analyDate) throws ParseException, IOException;

    List<TypicalCallTree> fetchTypicalCallTrees(String treeId, String analyDate) throws ParseException, IOException;
}
