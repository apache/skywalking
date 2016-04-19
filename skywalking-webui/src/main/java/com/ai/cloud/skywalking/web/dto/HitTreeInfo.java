package com.ai.cloud.skywalking.web.dto;

import com.ai.cloud.skywalking.web.dao.inter.IChainDetailDao;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.SQLException;
import java.util.*;

/**
 * Created by xin on 16-4-11.
 */
public class HitTreeInfo {
    private Logger logger = LogManager.getLogger(HitTreeInfo.class);
    private String treeId;
    private Map<String, String> hitTraceLevelId;
    private String entranceViewPoint;
    private String uid;

    public HitTreeInfo(String treeId, String uid) {
        this.treeId = treeId;
        this.uid = uid;
        this.hitTraceLevelId = new HashMap<String, String>();
    }

    public void addHitTraceLevelId(String traceLevelId, String viewPoint) {
        this.hitTraceLevelId.put(traceLevelId, viewPoint);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HitTreeInfo that = (HitTreeInfo) o;

        return treeId != null ? treeId.equals(that.treeId) : that.treeId == null;
    }

    @Override
    public int hashCode() {
        return treeId != null ? treeId.hashCode() : 0;
    }

    public String getTreeId() {
        return treeId;
    }

    public Map<String, String> getHitTraceLevelId() {
        return hitTraceLevelId;
    }

    private String[] spiltParentLevelIdAndLevelId(String levelId) {
        String[] result = new String[2];
        int indexOf = levelId.lastIndexOf(".");
        if (indexOf == -1) {
            // 根节点
            result[0] = "";
            result[1] = levelId;
            return result;
        }
        result[0] = levelId.substring(0, indexOf);
        result[1] = levelId.substring(indexOf + 1);

        return result;
    }

    public String getCurrentMonthAnlyTableName() {
        Calendar calendar = Calendar.getInstance();
        String monthSuffix = "/" + calendar.get(Calendar.YEAR);
        return getTreeId() + monthSuffix;
    }

    public void setEntranceViewPoint(String entranceViewPoint) {
        this.entranceViewPoint = entranceViewPoint;
    }

    public String getEntranceViewPoint() {
        return entranceViewPoint;
    }

    public void guessLevelIdAndSearchViewPoint(IChainDetailDao iChainDetailDao) throws SQLException {
        Set<String> levelIds = new HashSet<>();
        for (String levelId : levelIds) {
            String[] levelIdArray = levelId.split(".");
            if (levelId.length() < 2) {
                // 当前节点为根节点
                String tmpViewpoint = iChainDetailDao.queryChainViewPoint("0.0",
                        treeId, uid);
                if (tmpViewpoint != null) {
                    addHitTraceLevelId("0.0", tmpViewpoint);
                }
            } else {
                // 找上级
                guessPreLevelId(iChainDetailDao, levelId);
                //找下级
                guessNexLevelId(iChainDetailDao, levelId);
            }
        }
    }

    private void guessNexLevelId(IChainDetailDao iChainDetailDao, String levelId) throws SQLException {
        String[] levelIdArray = levelId.split(".");
        String parentLevelId = levelId.substring(0, levelId.lastIndexOf('.'));
        String nextLevelId = parentLevelId +"." + (Integer.parseInt(levelIdArray[levelIdArray.length - 1]) + 1);
        String tmpViewpoint = iChainDetailDao.queryChainViewPoint(nextLevelId,
                treeId, uid);
        if (tmpViewpoint != null) {
            addHitTraceLevelId(nextLevelId, tmpViewpoint);
        } else {
            levelIdArray = parentLevelId.split(".");
            parentLevelId = levelId.substring(0, levelId.lastIndexOf('.'));
            nextLevelId = parentLevelId + "." + (Integer.parseInt(levelIdArray[levelIdArray.length - 1]) + 1);
            tmpViewpoint = iChainDetailDao.queryChainViewPoint(nextLevelId,
                    treeId, uid);
            if (tmpViewpoint != null) {
                addHitTraceLevelId(nextLevelId, tmpViewpoint);
            }
        }

        logger.info("LevelId :{}, nextLevelId :{} ", levelId, nextLevelId);
    }

    private void guessPreLevelId(IChainDetailDao iChainDetailDao, String levelId) throws SQLException {
        String[] levelIdArray = levelId.split(".");
        String parentLevelId = levelId.substring(0, levelId.lastIndexOf('.'));
        if (levelIdArray.length == 2 && "0".equals(parentLevelId)) {
            //当前节点的父节点位根节点，不查询
            return;
        }

        if ("0".equals(levelIdArray[levelIdArray.length - 1])) {
            String tmpViewpoint = iChainDetailDao.queryChainViewPoint(parentLevelId,
                    treeId, uid);
            if (tmpViewpoint != null) {
                addHitTraceLevelId(parentLevelId, tmpViewpoint);
            }
        } else {
            parentLevelId += "." + (Integer.parseInt(levelIdArray[levelIdArray.length - 1]) - 1);
            String tmpViewpoint = iChainDetailDao.queryChainViewPoint(parentLevelId,
                    treeId, uid);
            if (tmpViewpoint != null) {
                addHitTraceLevelId(parentLevelId, tmpViewpoint);
            }
        }

        logger.info("LevelId :{}, preLevelId :{} ", levelId, parentLevelId);
    }

    public void guessLevelIdAndSearchViewPoint1(IChainDetailDao iChainDetailDao) throws SQLException {
        String tmpViewpoint;
        Set<String> leveIds = new HashSet<String>();
        leveIds.addAll(hitTraceLevelId.keySet());
        for (String levelId : leveIds) {
            String[] result = spiltParentLevelIdAndLevelId(levelId);
            if (result[0].length() == 0) {
                // 根节点，单独处理
                // 当前下级
                tmpViewpoint = iChainDetailDao.queryChainViewPoint(result[0] + ".0",
                        treeId, uid);
                if (tmpViewpoint != null) {
                    addHitTraceLevelId(getPreBrotherLevelId(result), tmpViewpoint);
                }
            } else {
                //找上级
                guessPreTraceLevelId(iChainDetailDao, levelId);
                //找下级
                guessNextTraceLevelId(iChainDetailDao, levelId);
            }
        }
        logger.debug("hitTraceLevelId : {}", hitTraceLevelId);
    }

    private void guessNextTraceLevelId(IChainDetailDao iChainDetailDao, String levelId) throws SQLException {
        String traceLevelId = getSubTraceLevelId(levelId);
        if (hitTraceLevelId.containsKey(traceLevelId)) {
            return;
        }
        String tmpViewpoint = iChainDetailDao.queryChainViewPoint(traceLevelId,
                treeId, uid);
        if (tmpViewpoint == null) {
            traceLevelId = getBrotherOfParentLevelId(levelId);
            if (traceLevelId.length() > 0)
                tmpViewpoint = iChainDetailDao.queryChainViewPoint(traceLevelId, treeId, uid);
        }

        if (tmpViewpoint != null) {
            addHitTraceLevelId(traceLevelId, tmpViewpoint);
        }
    }

    private void guessPreTraceLevelId(IChainDetailDao iChainDetailDao, String levelId) throws SQLException {
        String tmpViewpoint = null;
        String traceLevelId = null;
        String[] result = spiltParentLevelIdAndLevelId(levelId);
        if (result[0].length() > 0 && (Integer.parseInt(result[1]) - 1) != -1) {
            traceLevelId = getPreBrotherLevelId(result);
            tmpViewpoint = iChainDetailDao.queryChainViewPoint(traceLevelId,
                    treeId, uid);
        } else {
            if (result[0].length() > 0) {
                traceLevelId = result[0];
                tmpViewpoint = iChainDetailDao.queryChainViewPoint(traceLevelId,
                        treeId, uid);
            }
        }

        if (tmpViewpoint != null) {
            addHitTraceLevelId(traceLevelId, tmpViewpoint);
        }
    }

    private String getPreBrotherLevelId(String[] result) {
        return result[0] + "." + (Integer.parseInt(result[1]) - 1);
    }

    private String getBrotherOfParentLevelId(String levelId) {
        String[] result = spiltParentLevelIdAndLevelId(levelId);
        if (result[0].length() > 0) {
            result = spiltParentLevelIdAndLevelId(result[0]);
            if (result[1] != null && result[1].length() > 0) {
                return result[0] + (Integer.parseInt(result[1]) + 1);
            }
        }

        return "";
    }

    private String getSubTraceLevelId(String levelId) {
        String[] result = spiltParentLevelIdAndLevelId(levelId);
        return result[0] + "." + (Integer.parseInt(result[1]) + 1);
    }

    public String getUid() {
        return uid;
    }
}
