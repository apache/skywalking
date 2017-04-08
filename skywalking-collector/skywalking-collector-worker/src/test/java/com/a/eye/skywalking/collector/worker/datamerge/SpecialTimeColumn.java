package com.a.eye.skywalking.collector.worker.datamerge;

import com.a.eye.skywalking.collector.worker.Const;
import com.a.eye.skywalking.collector.worker.tools.DateTools;

/**
 * @author pengys5
 */
public enum SpecialTimeColumn {
    INSTANCE;

    public String specialTimeColumnChange(String value) {
        String[] values = value.split(Const.IDS_SPLIT);
        long changedTime = DateTools.changeToUTCSlice(Long.valueOf(values[0]));

        String changedValue = "";
        for (int i = 1; i < values.length; i++) {
            changedValue = changedValue + Const.ID_SPLIT + values[i];
        }
        return String.valueOf(changedTime) + changedValue;
    }

    public boolean isSpecialTimeColumn(String columnName) {
        String[] specialTimeColumns = {"nodeRefId"};
        for (String column : specialTimeColumns) {
            if (column.equals(columnName)) {
                return true;
            }
        }
        return false;
    }
}
