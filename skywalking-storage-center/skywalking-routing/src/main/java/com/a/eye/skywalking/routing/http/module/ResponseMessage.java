package com.a.eye.skywalking.routing.http.module;

import com.google.gson.JsonObject;

import fi.iki.elonen.NanoHTTPD;


/**
 * A {@link ResponseMessage} represent a status code and response messages for http-service.
 * <p>
 * Created by wusheng on 2017/1/13.
 */
public enum ResponseMessage {
    /**
     * Request span or Ack Span are received and parsed without any errors.
     */
    OK(NanoHTTPD.Response.Status.OK, "Store success"),
    /**
     * Request a no-supported service.
     */
    GET_NOT_SUPPORT(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED, "Get method not support"),
    /**
     * An internal error occurs.
     */
    SERVER_ERROR(NanoHTTPD.Response.Status.INTERNAL_ERROR, "Server error"),
    /**
     * No service found. Also mean not provide this service.
     */
    NOT_FOUND(NanoHTTPD.Response.Status.NOT_FOUND, "Not found");

    /**
     * The {@link String} represents the return message of the http services.
     * It is in the JSON format, and formatted by {@link com.google.gson.Gson}.
     */
    private String message;


    /**
     *
     */
    private NanoHTTPD.Response.IStatus status;

    ResponseMessage(NanoHTTPD.Response.Status status, String message) {
        this.status = status;
        JsonObject messageFormatter = new JsonObject();
        messageFormatter.addProperty("code", status.getRequestStatus());
        messageFormatter.addProperty("message", message);
        this.message = messageFormatter.toString();
    }

    public NanoHTTPD.Response.IStatus getStatus() {
        return status;
    }

    /**
     * @return the return message of the http services.
     */
    @Override
    public String toString() {
        return message;
    }
}
