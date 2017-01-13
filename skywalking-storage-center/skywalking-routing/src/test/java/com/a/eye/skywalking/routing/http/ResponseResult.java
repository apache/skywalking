package com.a.eye.skywalking.routing.http;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


/**
 * Created by xin on 2017/1/10.
 */
public class ResponseResult {
    private int statusCode;
    private String responseBody;

    public ResponseResult(String responseBody, int statusCode) {
       this.statusCode = statusCode;
       this.responseBody = responseBody;
    }

    public JsonObject getResponseMessage() {
        JsonParser jsonParser = new JsonParser();
       return (JsonObject) jsonParser.parse(responseBody);
    }

    public int getStatusCode(){
        return statusCode;
    }
}
