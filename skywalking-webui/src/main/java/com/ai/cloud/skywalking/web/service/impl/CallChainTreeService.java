package com.ai.cloud.skywalking.web.service.impl;

import com.ai.cloud.skywalking.web.dao.inter.ICallChainTreeDao;
import com.ai.cloud.skywalking.web.dao.inter.IChainDetailDao;
import com.ai.cloud.skywalking.web.entity.CallChainTree;
import com.ai.cloud.skywalking.web.service.inter.ICallChainTreeService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

@Service
public class CallChainTreeService implements ICallChainTreeService {

    private Logger logger = LogManager.getLogger(CallChainTreeService.class);

    @Autowired
    private IChainDetailDao iChainDetailDao;

    @Autowired
    private ICallChainTreeDao chainTreeDao;

    @Override
    public List<CallChainTree> queryCurrentMonthCallChainTree(String uid, String viewpoint) throws SQLException, IOException {
        List<String> chainTreeIds = iChainDetailDao.queryChainTreeIds(uid,viewpoint);
        logger.info("viewpoint key :{}, chainTreeIds : {}", viewpoint, chainTreeIds);
        List<CallChainTree> callChainTrees = new ArrayList<CallChainTree>();
        Calendar calendar = Calendar.getInstance();
        String monthSuffix = "/" + calendar.get(Calendar.YEAR) + "-" + (calendar.get(Calendar.MONTH) + 1);
        for (String treeId : chainTreeIds) {
            CallChainTree chainTree = chainTreeDao.queryTreeId(treeId + monthSuffix);
            if (chainTree == null) {
                continue;
            }
            callChainTrees.add(chainTree);
        }
        logger.info("viewpoint key :{}, chainTree size : {}", viewpoint, callChainTrees.size());
        return callChainTrees;
    }
}
