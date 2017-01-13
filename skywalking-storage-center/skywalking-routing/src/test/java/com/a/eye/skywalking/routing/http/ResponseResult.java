package com.a.eye.skywalking.routing.http;

import com.google.gson.Gson;

import com.a.eye.skywalking.routing.http.module.ResponseMessage;

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

    public ResponseMessage getResponseMessage() {
        return new Gson().fromJson(responseBody, ResponseMessage.class);
    }

    public int getStatusCode(){
        return statusCode;
    }
}
