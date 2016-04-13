package com.ai.cloud.skywalking.web.dto;

import com.ai.cloud.skywalking.web.dao.inter.IChainDetailDao;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.SQLException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

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

        result[0] = levelId.substring(0, indexOf - 1);
        result[1] = levelId.substring(indexOf + 1);

        return result;
    }

    public String getCurrentMonthAnlyTableName() {
        Calendar calendar = Calendar.getInstance();
        String monthSuffix = "/" + calendar.get(Calendar.YEAR) + "-" + (calendar.get(Calendar.MONTH) + 1);
        return getTreeId() + monthSuffix;
    }

    public void setEntranceViewPoint(String entranceViewPoint) {
        this.entranceViewPoint = entranceViewPoint;
    }

    public String getEntranceViewPoint() {
        return entranceViewPoint;
    }

    public void guessLevelIdAndSearchViewPoint(IChainDetailDao iChainDetailDao) throws SQLException {
        String tmpViewpoint;
        for (String levelId : hitTraceLevelId.keySet()) {
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
                guessPreTraceLevelId(iChainDetailDao, result);
                //找下级
                guessNextTraceLevelId(iChainDetailDao, result);
            }
        }
        logger.debug("hitTraceLevelId : {}", hitTraceLevelId);
    }

    private void guessNextTraceLevelId(IChainDetailDao iChainDetailDao, String[] result) throws SQLException {
        String tmpViewpoint = iChainDetailDao.queryChainViewPoint(getSubTraceLevelId(result),
                treeId, uid);
        String traceLevelId = null;
        if (tmpViewpoint != null) {
            traceLevelId = getSubTraceLevelId(result);
        } else {
            traceLevelId = getBrotherOfParentLevelId(result);
            if (traceLevelId != null && traceLevelId.length() > 0)
                tmpViewpoint = iChainDetailDao.queryChainViewPoint(traceLevelId, treeId, uid);
        }

        if (tmpViewpoint != null) {
            addHitTraceLevelId(traceLevelId, tmpViewpoint);
        }
    }

    private void guessPreTraceLevelId(IChainDetailDao iChainDetailDao, String[] result) throws SQLException {
        String tmpViewpoint = null;
        String traceLevelId = null;
        if ((Integer.parseInt(result[1]) - 1) != -1) {
            traceLevelId = getPreBrotherLevelId(result);
            tmpViewpoint = iChainDetailDao.queryChainViewPoint(traceLevelId,
                    treeId, uid);
        } else {
            String[] resultA = spiltParentLevelIdAndLevelId(result[0]);
            if (result[0].length() != 0) {
                traceLevelId = resultA[0];
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

    private String getBrotherOfParentLevelId(String[] result) {
        String[] resultB = spiltParentLevelIdAndLevelId(result[0]);
        if (resultB[1] != null && resultB.length > 0) {
            return resultB[0] + (Integer.parseInt(resultB[1]) + 1);
        }

        return "";
    }

    private String getSubTraceLevelId(String[] result) {
        return result[0] + "." + (Integer.parseInt(result[1]) + 1);
    }
}
