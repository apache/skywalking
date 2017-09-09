package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonObject;

/**
 * @author pengys5
 */
public interface IGCMetricDAO {

    GCCount getGCCount(long[] timeBuckets, int instanceId);

    JsonObject getMetric(int instanceId, long timeBucket);

    JsonObject getMetric(int instanceId, long startTimeBucket, long endTimeBucket);

    class GCCount {
        private int young;
        private int old;
        private int full;

        public int getYoung() {
            return young;
        }

        public int getOld() {
            return old;
        }

        public int getFull() {
            return full;
        }

        public void setYoung(int young) {
            this.young = young;
        }

        public void setOld(int old) {
            this.old = old;
        }

        public void setFull(int full) {
            this.full = full;
        }
    }
}
