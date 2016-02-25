package com.ai.cloud.skywalking.analysis.chain2summary;

import com.ai.cloud.skywalking.analysis.categorize2chain.ChainNodeSpecificTimeWindowSummary;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.apache.hadoop.io.Writable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class ChainSpecificTimeSummary implements Writable {
    private String cId;
    private String userId;
    private String entranceNodeToken;
    //key : TraceLevelId
    private Map<String, ChainNodeSpecificTimeWindowSummary> summaryMap;
    private long summaryTimestamp;

    public ChainSpecificTimeSummary() {
    }

    public ChainSpecificTimeSummary(String rowKey) throws ParseException {
        String[] splitValue = rowKey.split("-");
        this.cId = splitValue[0];
        this.userId = splitValue[1];
        this.summaryTimestamp = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").parse(splitValue[2]).getTime();
        summaryMap = new HashMap<String, ChainNodeSpecificTimeWindowSummary>();
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.write(new Gson().toJson(this).getBytes());
    }

    @Override
    public void readFields(DataInput in) throws IOException {
        JsonObject jsonObject = (JsonObject) new JsonParser().parse(in.readLine());
        cId = jsonObject.get("cId").getAsString();
        userId = jsonObject.get("userId").getAsString();
        if (jsonObject.get("entranceNodeToken") == null) {
            throw new IOException("No entryNode Token CId[" + cId + "]");
        }
        entranceNodeToken = jsonObject.get("entranceNodeToken").getAsString();
        summaryMap = new Gson().fromJson(jsonObject.get("summaryMap").toString(),
                new TypeToken<Map<String, ChainNodeSpecificTimeWindowSummary>>() {
                }.getType());
    }

    public void addChainNodeSummaryResult(String summaryResult) {
        ChainNodeSpecificTimeWindowSummary chainNodeSpecificTimeWindowSummary = new Gson().
                fromJson(summaryResult, ChainNodeSpecificTimeWindowSummary.class);

        if ("0".equals(chainNodeSpecificTimeWindowSummary.getTraceLevelId())) {
            this.entranceNodeToken = chainNodeSpecificTimeWindowSummary.getNodeToken();
        }

        summaryMap.put(chainNodeSpecificTimeWindowSummary.getTraceLevelId(), chainNodeSpecificTimeWindowSummary);
    }

    public String buildMapperKey() {
        return userId + ":" + entranceNodeToken;
    }


    public String getcId() {
        return cId;
    }

    public String getHourKey() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date(summaryTimestamp));
        return calendar.get(Calendar.YEAR) + "/" + (calendar.get(Calendar.MONTH) + 1) + "/"  +calendar.get(Calendar.DAY_OF_MONTH)
                + " " + calendar.get(Calendar.HOUR) + ":00:00";
    }

    public String getDayKey() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date(summaryTimestamp));
        return calendar.get(Calendar.YEAR) + "-" + (calendar.get(Calendar.MONTH) + 1)  + "-" + (calendar.get(Calendar.DAY_OF_MONTH) + 1);
    }

    public String getMonthKey() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date(summaryTimestamp));
        return calendar.get(Calendar.YEAR) + "-" + (calendar.get(Calendar.MONTH) + 1);
    }

    public String getYearKey() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date(summaryTimestamp));
        return String.valueOf(calendar.get(Calendar.YEAR));
    }

    public String getUserId() {
        return userId;
    }

    public Map<String, ChainNodeSpecificTimeWindowSummary> getSummaryMap() {
        return summaryMap;
    }

    public long getSummaryTimestamp() {
        return summaryTimestamp;
    }
}
