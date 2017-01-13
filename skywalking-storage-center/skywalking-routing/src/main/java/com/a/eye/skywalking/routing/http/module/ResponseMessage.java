package com.a.eye.skywalking.routing.http.module;

import com.google.gson.JsonObject;

/**
 * A {@link ResponseMessage} represent a status code and response messages for http-service.
 * <p>
 * Created by wusheng on 2017/1/13.
 */
public enum ResponseMessage {
    /**
     * Request span or Ack Span are received and parsed without any errors.
     */
    OK(200, "Store success"),
    /**
     * Request a no-supported service.
     */
    GET_NOT_SUPPORT(405, "Get method not support"),
    /**
     * An internal error occurs.
     */
    SERVER_ERROR(500, "Server error"),
    /**
     * No service found. Also mean not provide this service.
     */
    NOT_FOUND(404, "Not found");

    /**
     * The {@link String} represents the return message of the http services.
     * It is in the JSON format, and formatted by {@link com.google.gson.Gson}.
     */
    private String message;

    ResponseMessage(int code, String message) {
        JsonObject messageFormatter = new JsonObject();
        messageFormatter.addProperty("code", code);
        messageFormatter.addProperty("message", message);
        this.message = messageFormatter.toString();
    }

    /**
     * @return the return message of the http services.
     */
    @Override
    public String toString() {
        return message;
    }
}
