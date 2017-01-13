package com.a.eye.skywalking.routing.http.module;


import com.a.eye.skywalking.network.dependencies.com.google.gson.Gson;

public class ResponseMessage {
    public static final ResponseMessage OK = new ResponseMessage(200, "Store success");
    public static final ResponseMessage REQUEST_METHOD_NOT_SUPPORT = new ResponseMessage(403, "Request method " +
            "not support");
    public static final ResponseMessage SERVER_ERROR = new ResponseMessage(500, "Server error");
    public static final ResponseMessage URL_NOT_FOUND = new ResponseMessage(404, "Not found");

    /**
     * Response code:
     * 200 -- store success
     * 403 -- request method not support
     * 500 -- server error
     * 404 -- not found
     */
    private int code;
    private String message;

    ResponseMessage(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }
}
