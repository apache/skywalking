package com.ai.cloud.skywalking.web.service.impl;

import com.ai.cloud.skywalking.web.dao.inter.ICallChainTreeDao;
import com.ai.cloud.skywalking.web.dao.inter.IChainDetailDao;
import com.ai.cloud.skywalking.web.dto.AnlyResult;
import com.ai.cloud.skywalking.web.dto.CallChainNode;
import com.ai.cloud.skywalking.web.dto.HitTreeInfo;
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
    private IChainDetailDao chainDetailDao;

    @Autowired
    private ICallChainTreeDao chainTreeDao;

    @Override
    public List<CallChainTree> queryCallChainTreeByKey(String uid, String viewpoint, int pageSize) throws SQLException, IOException {
        List<CallChainTree> trees = new ArrayList<CallChainTree>();
        List<HitTreeInfo> hitTrees = chainDetailDao.queryChainTreeIds(uid, viewpoint, pageSize);
        for (HitTreeInfo hitTree : hitTrees) {
            CallChainTree chainTree = new CallChainTree(hitTree.getTreeId());
            chainTree.setEntranceViewpoint(chainDetailDao.queryChainViewPoint("0", hitTree.getTreeId(), uid));

            chainTree.addHitNodes(hitTree.getHitTraceLevelId());
            //臆测TraceLevelId
            chainTree.addGuessNodesAndRemoveDuplicate(doGuessNodes(hitTree));
            //获取统计结果
            AnlyResult anlyResult = chainTreeDao.queryEntranceAnlyResult("0@" + chainTree.getEntranceViewpoint(), hitTree.getCurrentMonthAnlyTableName());
            chainTree.setEntranceAnlyResult(anlyResult);
            chainTree.sortNodes();
            //美化显示
            chainTree.beautiViewPointString(viewpoint);
            trees.add(chainTree);
        }
        return trees;
    }

    private List<CallChainNode> doGuessNodes(HitTreeInfo hitTraceLevelIds) throws SQLException {
        List<CallChainNode> guessNodes = new ArrayList<CallChainNode>();
        for (String traceLevelId : hitTraceLevelIds.getHitTraceLevelId().keySet()) {
            //
            CallChainNode preTraceLevelNode = guessPreTraceLevelId(traceLevelId, hitTraceLevelIds.getTreeId(), hitTraceLevelIds.getUid());
            if (preTraceLevelNode != null) {
                guessNodes.add(preTraceLevelNode);
                logger.info("treeId:{}, traceLevelId :{}, preTraceLevelId:{}", hitTraceLevelIds.getTreeId(), traceLevelId, preTraceLevelNode.getTraceLevelId());
            }

            //
            CallChainNode nextTraceLevelNode = guessNextTraceLevelId(traceLevelId, hitTraceLevelIds.getTreeId(), hitTraceLevelIds.getUid());
            if (nextTraceLevelNode != null) {
                guessNodes.add(nextTraceLevelNode);
                logger.info("treeId:{}, traceLevelId :{}, nextTraceLevelId:{}", hitTraceLevelIds.getTreeId(), traceLevelId, nextTraceLevelNode.getTraceLevelId());
            }

        }

        return guessNodes;
    }

    private CallChainNode guessNextTraceLevelId(String traceLevelId, String treeId, String uid) throws SQLException {
        String[] levelIdArray = traceLevelId.split("\\.");
        if (traceLevelId.lastIndexOf('.') == -1) {
            return null;
        }
        String parentLevelId = traceLevelId.substring(0, traceLevelId.lastIndexOf('.'));
        if (levelIdArray.length == 0)
            return null;
        String subLevelId = parentLevelId + "." + (Integer.parseInt(levelIdArray[levelIdArray.length - 1]) + 1);
        String tmpViewpoint = chainDetailDao.queryChainViewPoint(subLevelId,
                treeId, uid);
        CallChainNode result = null;
        if (tmpViewpoint == null) {
            levelIdArray = parentLevelId.split("\\.");
            if (levelIdArray.length != 1) {
                // 不为根节点
                String grandParentLevelId = traceLevelId.substring(0, parentLevelId.lastIndexOf('.'));
                subLevelId = grandParentLevelId + "." + (Integer.parseInt(levelIdArray[levelIdArray.length - 1]) + 1);
                tmpViewpoint = chainDetailDao.queryChainViewPoint(subLevelId,
                        treeId, uid);
                logger.info("treeId:{} TreeLevelId:{}, subLevel[{}] is null, find the brother of parent LevelId :{}", treeId, subLevelId, traceLevelId, subLevelId);
            }
        }

        if (tmpViewpoint != null) {
            result = new CallChainNode(subLevelId, tmpViewpoint, true);
        }

        return result;
    }

    private CallChainNode guessPreTraceLevelId(String traceLevelId, String treeId, String uid) throws SQLException {
        String[] levelIdArray = traceLevelId.split("\\.");
        if (levelIdArray.length <= 2) {
            //上级节点为根节点，不用臆测，页面自动展示
            return null;
        }

        CallChainNode result = null;
        String parentLevelId = traceLevelId.substring(0, traceLevelId.lastIndexOf('.'));
        if ("0".equals(levelIdArray[levelIdArray.length - 1])) {
            // 本机节点为0，找父级
            String tmpViewpoint = chainDetailDao.queryChainViewPoint(parentLevelId,
                    treeId, uid);
            if (tmpViewpoint != null) {
                result = new CallChainNode(parentLevelId, tmpViewpoint, true);
            }
        } else {
            // 本机节点不为0，找上级
            String preLevelId = parentLevelId + "." + (Integer.parseInt(levelIdArray[levelIdArray.length - 1]) - 1);
            String tmpViewpoint = chainDetailDao.queryChainViewPoint(preLevelId,
                    treeId, uid);
            if (tmpViewpoint != null) {
                result = new CallChainNode(preLevelId, tmpViewpoint, true);
            }
        }

        return result;
    }

}
