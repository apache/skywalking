package org.skywalking.apm.collector.ui.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.core.util.ColumnNameUtils;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.core.util.ObjectUtils;
import org.skywalking.apm.collector.core.util.StringUtils;
import org.skywalking.apm.collector.storage.define.serviceref.ServiceReferenceTable;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.skywalking.apm.collector.ui.cache.ServiceIdCache;
import org.skywalking.apm.collector.ui.cache.ServiceNameCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * @author pengys5, clevertension
 */
public class ServiceReferenceH2DAO extends H2DAO implements IServiceReferenceDAO {
    private final Logger logger = LoggerFactory.getLogger(ServiceReferenceH2DAO.class);

    private static final String GET_SRV_REF_LOAD1 = "select {4}, {5}, {6}, {7}, sum({8}) as cnt1, sum({9}) as cnt2, sum({10}) as cnt3" +
            ",sum({11}) as cnt4, sum({12}) cnt5, sum({13}) as cnt6, sum({14}) as cnt7 from {0} where {1} >= ? and {1} <= ? and {2} = ? and {3} = ? group by {4}, {5}, {6}, {7}";
    private static final String GET_SRV_REF_LOAD2 = "select {3}, {4}, {5}, {6}, sum({7}) as cnt1, sum({8}) as cnt2, sum({9}) as cnt3" +
            ",sum({10}) as cnt4, sum({11}) cnt5, sum({12}) as cnt6, sum({13}) as cnt7 from {0} where {1} >= ? and {1} <= ? and {2} = ? group by {3}, {4}, {5}, {6}";

    @Override public JsonArray load(int entryServiceId, long startTime, long endTime) {
        H2Client client = getClient();
        String sql = MessageFormat.format(GET_SRV_REF_LOAD1, ServiceReferenceTable.TABLE,
                ServiceReferenceTable.COLUMN_TIME_BUCKET, ServiceReferenceTable.COLUMN_ENTRY_SERVICE_ID, ServiceReferenceTable.COLUMN_ENTRY_SERVICE_NAME,
                ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID, ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID,
                ServiceReferenceTable.COLUMN_FRONT_SERVICE_NAME, ServiceReferenceTable.COLUMN_BEHIND_SERVICE_NAME,
                ServiceReferenceTable.COLUMN_S1_LTE, ServiceReferenceTable.COLUMN_S3_LTE, ServiceReferenceTable.COLUMN_S5_LTE,
                ServiceReferenceTable.COLUMN_S5_GT, ServiceReferenceTable.COLUMN_ERROR, ServiceReferenceTable.COLUMN_SUMMARY,
                ServiceReferenceTable.COLUMN_COST_SUMMARY);
        String entryServiceName = ServiceNameCache.get(entryServiceId);
        Object[] params = new Object[]{startTime, endTime, entryServiceId, entryServiceName};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                Map<String, JsonObject> serviceReferenceMap = new LinkedHashMap<>();

                int frontServiceId = rs.getInt(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID);
                if (frontServiceId != 0) {
                    parseSubAggregate(serviceReferenceMap, rs, frontServiceId);
                }

                String frontServiceName = rs.getString(ServiceReferenceTable.COLUMN_FRONT_SERVICE_NAME);
                if (StringUtils.isNotEmpty(frontServiceName)) {
                    String[] serviceNames = frontServiceName.split(Const.ID_SPLIT);
                    int frontServiceId1 = ServiceIdCache.getForUI(Integer.parseInt(serviceNames[0]), serviceNames[1]);
                    parseSubAggregate(serviceReferenceMap, rs, frontServiceId1);
                }

                JsonArray serviceReferenceArray = new JsonArray();
                JsonObject rootServiceReference = findRoot(serviceReferenceMap);
                if (ObjectUtils.isNotEmpty(rootServiceReference)) {
                    serviceReferenceArray.add(rootServiceReference);
                    String id = rootServiceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID)) + Const.ID_SPLIT + rootServiceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID));
                    serviceReferenceMap.remove(id);

                    int rootServiceId = rootServiceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID)).getAsInt();
                    sortAsTree(rootServiceId, serviceReferenceArray, serviceReferenceMap);
                }
                return serviceReferenceArray;
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    @Override public JsonArray load(String entryServiceName, int entryApplicationId, long startTime, long endTime) {
        H2Client client = getClient();
        String sql = MessageFormat.format(GET_SRV_REF_LOAD2, ServiceReferenceTable.TABLE,
                ServiceReferenceTable.COLUMN_TIME_BUCKET, ServiceReferenceTable.COLUMN_ENTRY_SERVICE_NAME,
                ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID, ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID,
                ServiceReferenceTable.COLUMN_FRONT_SERVICE_NAME, ServiceReferenceTable.COLUMN_BEHIND_SERVICE_NAME,
                ServiceReferenceTable.COLUMN_S1_LTE, ServiceReferenceTable.COLUMN_S3_LTE, ServiceReferenceTable.COLUMN_S5_LTE,
                ServiceReferenceTable.COLUMN_S5_GT, ServiceReferenceTable.COLUMN_ERROR, ServiceReferenceTable.COLUMN_SUMMARY,
                ServiceReferenceTable.COLUMN_COST_SUMMARY);
        entryServiceName = entryApplicationId + Const.ID_SPLIT + entryServiceName;
        Object[] params = new Object[]{startTime, endTime, entryServiceName};
        int entryServiceId = ServiceIdCache.get(entryApplicationId, entryServiceName);
        if (entryServiceId != 0) {
            sql = MessageFormat.format(GET_SRV_REF_LOAD1, ServiceReferenceTable.TABLE,
                    ServiceReferenceTable.COLUMN_TIME_BUCKET, ServiceReferenceTable.COLUMN_ENTRY_SERVICE_ID, ServiceReferenceTable.COLUMN_ENTRY_SERVICE_NAME,
                    ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID, ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID,
                    ServiceReferenceTable.COLUMN_FRONT_SERVICE_NAME, ServiceReferenceTable.COLUMN_BEHIND_SERVICE_NAME,
                    ServiceReferenceTable.COLUMN_S1_LTE, ServiceReferenceTable.COLUMN_S3_LTE, ServiceReferenceTable.COLUMN_S5_LTE,
                    ServiceReferenceTable.COLUMN_S5_GT, ServiceReferenceTable.COLUMN_ERROR, ServiceReferenceTable.COLUMN_SUMMARY,
                    ServiceReferenceTable.COLUMN_COST_SUMMARY);
            params = new Object[]{startTime, endTime, entryServiceId, entryServiceName};
        }

        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                Map<String, JsonObject> serviceReferenceMap = new LinkedHashMap<>();

                int frontServiceId = rs.getInt(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID);
                if (frontServiceId != 0) {
                    parseSubAggregate(serviceReferenceMap, rs, frontServiceId);
                }

                String frontServiceName = rs.getString(ServiceReferenceTable.COLUMN_FRONT_SERVICE_NAME);
                if (StringUtils.isNotEmpty(frontServiceName)) {
                    String[] serviceNames = frontServiceName.split(Const.ID_SPLIT);
                    int frontServiceId1 = ServiceIdCache.getForUI(Integer.parseInt(serviceNames[0]), serviceNames[1]);
                    parseSubAggregate(serviceReferenceMap, rs, frontServiceId1);
                }

                JsonArray serviceReferenceArray = new JsonArray();
                JsonObject rootServiceReference = findRoot(serviceReferenceMap);
                if (ObjectUtils.isNotEmpty(rootServiceReference)) {
                    serviceReferenceArray.add(rootServiceReference);
                    String id = rootServiceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID)) + Const.ID_SPLIT + rootServiceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID));
                    serviceReferenceMap.remove(id);

                    int rootServiceId = rootServiceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID)).getAsInt();
                    sortAsTree(rootServiceId, serviceReferenceArray, serviceReferenceMap);
                }
                return serviceReferenceArray;
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    private void parseSubAggregate(Map<String, JsonObject> serviceReferenceMap,
                                   ResultSet rs,
                                   int frontServiceId) {
        try {
            int behindServiceId = rs.getInt(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID);
            if (behindServiceId != 0) {
                long s1LteSum = rs.getLong("cnt1");
                long s3LteSum = rs.getLong("cnt2");
                long s5LteSum = rs.getLong("cnt3");
                long s5GtSum = rs.getLong("cnt3");
                long error = rs.getLong("cnt3");
                long summary = rs.getLong("cnt3");
                long costSum = rs.getLong("cnt3");

                String frontServiceName = ServiceNameCache.getForUI(frontServiceId);
                if (StringUtils.isNotEmpty(frontServiceName)) {
                    frontServiceName = frontServiceName.split(Const.ID_SPLIT)[1];
                }
                String behindServiceName = ServiceNameCache.getForUI(behindServiceId);
                if (StringUtils.isNotEmpty(frontServiceName)) {
                    behindServiceName = behindServiceName.split(Const.ID_SPLIT)[1];
                }

                JsonObject serviceReference = new JsonObject();
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID), frontServiceId);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_FRONT_SERVICE_NAME), frontServiceName);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID), behindServiceId);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_NAME), behindServiceName);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_S1_LTE), s1LteSum);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_S3_LTE), s3LteSum);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_S5_LTE), s5LteSum);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_S5_GT), s5GtSum);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_ERROR), error);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_SUMMARY), summary);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_COST_SUMMARY), costSum);
                merge(serviceReferenceMap, serviceReference);
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }

        try {
            String behindServiceName = rs.getString(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_NAME);
            if (StringUtils.isNotEmpty(behindServiceName)) {
                long s1LteSum = rs.getLong("cnt1");
                long s3LteSum = rs.getLong("cnt2");
                long s5LteSum = rs.getLong("cnt3");
                long s5GtSum = rs.getLong("cnt3");
                long error = rs.getLong("cnt3");
                long summary = rs.getLong("cnt3");
                long costSum = rs.getLong("cnt3");

                String frontServiceName = ServiceNameCache.getForUI(frontServiceId);
                String[] serviceNames = behindServiceName.split(Const.ID_SPLIT);
                int behindServiceId = ServiceIdCache.getForUI(Integer.parseInt(serviceNames[0]), serviceNames[1]);
                behindServiceName = serviceNames[1];

                JsonObject serviceReference = new JsonObject();
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID), frontServiceId);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_FRONT_SERVICE_NAME), frontServiceName);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID), behindServiceId);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_NAME), behindServiceName);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_S1_LTE), s1LteSum);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_S3_LTE), s3LteSum);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_S5_LTE), s5LteSum);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_S5_GT), s5GtSum);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_ERROR), error);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_SUMMARY), summary);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_COST_SUMMARY), costSum);
                merge(serviceReferenceMap, serviceReference);
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private void merge(Map<String, JsonObject> serviceReferenceMap, JsonObject serviceReference) {
        String id = serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID)) + Const.ID_SPLIT + serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID));

        if (serviceReferenceMap.containsKey(id)) {
            JsonObject reference = serviceReferenceMap.get(id);
            add(reference, serviceReference, ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_S1_LTE));
            add(reference, serviceReference, ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_S3_LTE));
            add(reference, serviceReference, ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_S5_LTE));
            add(reference, serviceReference, ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_S5_GT));
            add(reference, serviceReference, ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_ERROR));
            add(reference, serviceReference, ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_SUMMARY));
            add(reference, serviceReference, ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_COST_SUMMARY));
        } else {
            serviceReferenceMap.put(id, serviceReference);
        }
    }

    private void add(JsonObject oldReference, JsonObject newReference, String key) {
        long oldValue = oldReference.get(key).getAsLong();
        long newValue = newReference.get(key).getAsLong();
        oldReference.addProperty(key, oldValue + newValue);
    }

    private JsonObject findRoot(Map<String, JsonObject> serviceReferenceMap) {
        for (JsonObject serviceReference : serviceReferenceMap.values()) {
            int frontServiceId = serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID)).getAsInt();
            if (frontServiceId == 1) {
                return serviceReference;
            }
        }
        return null;
    }

    private void sortAsTree(int serviceId, JsonArray serviceReferenceArray,
                            Map<String, JsonObject> serviceReferenceMap) {
        Iterator<JsonObject> iterator = serviceReferenceMap.values().iterator();
        while (iterator.hasNext()) {
            JsonObject serviceReference = iterator.next();
            int frontServiceId = serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID)).getAsInt();
            if (serviceId == frontServiceId) {
                serviceReferenceArray.add(serviceReference);

                int behindServiceId = serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID)).getAsInt();
                sortAsTree(behindServiceId, serviceReferenceArray, serviceReferenceMap);
            }
        }
    }
}
