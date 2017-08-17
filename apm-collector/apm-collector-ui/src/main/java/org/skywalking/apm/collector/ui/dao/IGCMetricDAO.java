package org.skywalking.apm.collector.ui.dao;

/**
 * @author pengys5
 */
public interface IGCMetricDAO {

    GCCount getGCCount(long timestamp, int instanceId);

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
