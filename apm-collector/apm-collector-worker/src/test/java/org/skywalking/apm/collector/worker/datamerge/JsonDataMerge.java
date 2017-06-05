package org.skywalking.apm.collector.worker.datamerge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.Assert;
import org.skywalking.apm.collector.worker.tools.DateTools;
import org.skywalking.apm.collector.worker.tools.JsonFileReader;

import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.Map;

/**
 * @author pengys5
 */
public enum JsonDataMerge {
    INSTANCE;

    private String path = this.getClass().getResource("/").getPath();

    public void merge(String expectJsonFile, JsonArray actualData) throws FileNotFoundException {
        Gson gson = new Gson();
        String jsonStrData = JsonFileReader.INSTANCE.read(path + expectJsonFile);
        JsonArray expectJsonArray = gson.fromJson(jsonStrData, JsonArray.class);

        for (int i = 0; i < expectJsonArray.size(); i++) {
            JsonObject expectJson = expectJsonArray.get(i).getAsJsonObject();
            mergeData(expectJson, actualData.get(i).getAsJsonObject());
        }
    }

    private void mergeData(JsonObject expectData, JsonObject actualData) {
        Iterator<Map.Entry<String, JsonElement>> iterator = expectData.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonElement> entry = iterator.next();
            String key = entry.getKey();
            String value = null;
            if (!entry.getValue().isJsonNull()) {
                value = entry.getValue().getAsString();
            }

            if (entry.getValue().isJsonNull()) {
                Assert.assertEquals(true, actualData.get(key).isJsonNull());
            } else {
                if (key.equals("timeSlice") || key.equals("minute") || key.equals("hour") || key.equals("day")) {
                    value = String.valueOf(DateTools.changeToUTCSlice(Long.valueOf(value)));
                }
                if (SpecialTimeColumn.INSTANCE.isSpecialTimeColumn(key)) {
                    String changedValue = SpecialTimeColumn.INSTANCE.specialTimeColumnChange(value);
                    Assert.assertEquals(changedValue, actualData.get(key).getAsString());
                } else {
                    Assert.assertEquals(value, actualData.get(key).getAsString());
                }
            }
        }
    }
}
