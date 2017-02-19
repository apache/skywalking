package com.a.eye.skywalking.trace.tag;

import com.a.eye.skywalking.trace.Span;

/**
 * The span tags are supported by sky-walking engine.
 * As default, all tags will be stored, but these ones have particular meanings.
 *
 * Created by wusheng on 2017/2/17.
 */
public final class Tags {
    private Tags() {
    }

    /**
     * URL records the url of the incoming request.
     */
    public static final StringTag URL = new StringTag("url");

    /**
     * STATUS_CODE records the http status code of the response.
     */
    public static final IntTag STATUS_CODE = new IntTag("status_code");

    /**
     * SPAN_KIND hints at the relationship between spans, e.g. client/server.
     */
    public static final StringTag SPAN_KIND = new StringTag("span.kind");

    /**
     * A constant for setting the span kind to indicate that it represents a server span.
     */
    public static final String SPAN_KIND_SERVER = "server";

    /**
     * A constant for setting the span kind to indicate that it represents a client span.
     */
    public static final String SPAN_KIND_CLIENT = "client";

    /**
     * SPAN_LAYER represents the kind of span.
     * 
     * e.g.
     * db=database;
     * rpc=Remote Procedure Call Framework, like motan, thift;
     * nosql=something like redis/memcache
     */
    public static final class SPAN_LAYER {
        private static StringTag SPAN_LAYER_TAG = new StringTag("span.layer");

        private static final String RDB_LAYER = "rdb";
        private static final String RPC_FRAMEWORK_LAYER = "rpc";
        private static final String NOSQL_LAYER = "nosql";
        private static final String HTTP_LAYER = "http";

        public static void asRDB(Span span) {
            SPAN_LAYER_TAG.set(span, RDB_LAYER);
        }

        public static void asRPCFramework(Span span) {
            SPAN_LAYER_TAG.set(span, RPC_FRAMEWORK_LAYER);
        }

        public static void asNoSQL(Span span) {
            SPAN_LAYER_TAG.set(span, NOSQL_LAYER);
        }

        public static void asHttp(Span span) {
            SPAN_LAYER_TAG.set(span, HTTP_LAYER);
        }

        public static String get(Span span) {
            return SPAN_LAYER_TAG.get(span);
        }

        public static boolean isRDB(Span span) {
            return RDB_LAYER.equals(get(span));
        }

        public static boolean isRPCFramework(Span span) {
            return RPC_FRAMEWORK_LAYER.equals(get(span));
        }

        public static boolean isNoSQL(Span span) {
            return NOSQL_LAYER.equals(get(span));
        }

        public static boolean isHttp(Span span) {
            return HTTP_LAYER.equals(get(span));
        }
    }

    /**
     * COMPONENT is a low-cardinality identifier of the module, library, or package that is instrumented.
     * Like dubbo/dubbox/motan
     */
    public static final StringTag COMPONENT = new StringTag("component");

    /**
     * ERROR indicates whether a Span ended in an error state.
     */
    public static final BooleanTag ERROR = new BooleanTag("error");

    /**
     * PEER_HOST records host address of the peer, maybe IPV4, IPV6 or hostname.
     */
    public static final StringTag PEER_HOST = new StringTag("peer.host");

    /**
     * DB_URL records the url of the database access.
     */
    public static final StringTag DB_URL = new StringTag("db.url");

    /**
     * DB_STATEMENT records the sql statement of the database access.
     */
    public static final StringTag DB_STATEMENT = new StringTag("db.statement");
}
