package com.ai.cloud.skywalking.web.service.impl;

import com.ai.cloud.skywalking.web.dto.HitTreeInfo;
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
        List<HitTreeInfo> hitTreeInfos = iChainDetailDao.queryChainTreeIds(uid, viewpoint);
        for (HitTreeInfo hitTreeInfo : hitTreeInfos) {
            hitTreeInfo.guessLevelIdAndSearchViewPoint(iChainDetailDao);
        }

        logger.info("viewpoint key :{}, chainTreeIds : {}", viewpoint, hitTreeInfos);
        List<CallChainTree> callChainTrees = new ArrayList<CallChainTree>();
        for (HitTreeInfo hitTreeInfo : hitTreeInfos) {
            String entranceViewpoint = iChainDetailDao.queryChainViewPoint("0", hitTreeInfo.getTreeId(), uid);
            if (entranceViewpoint == null || entranceViewpoint.length() == 0) {
                continue;
            }
            hitTreeInfo.setEntranceViewPoint(entranceViewpoint);
            CallChainTree chainTree = new CallChainTree(hitTreeInfo.getTreeId(), entranceViewpoint);
            chainTree.setEntranceAnlyResult(chainTreeDao.queryEntranceAnlyResult(
                    "0@" + hitTreeInfo.getEntranceViewPoint(),
                    hitTreeInfo.getCurrentMonthAnlyTableName()));
            chainTree.addNodes(hitTreeInfo.getHitTraceLevelId());
            callChainTrees.add(chainTree);
        }
        logger.info("viewpoint key :{}, chainTree size : {}", viewpoint, callChainTrees.size());
        return callChainTrees;
    }
}
