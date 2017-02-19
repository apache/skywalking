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
     *  HTTP_URL records the url of the incoming request.
     */
    public static final StringTag HTTP_URL = new StringTag("http.url");

    /**
     *  HTTP_STATUS records the http status code of the response.
     */
    public static final IntTag HTTP_STATUS = new IntTag("http.status_code");

    /**
     *
     */
    public static final StringTag SPAN_KIND = new StringTag("span.kind");

    /**
     *  SPAN_LAYER represents the kind of span.
     *  e.g. db=database, rpc=Remote Procedure Call, nosql=something like redis/memcache
     */
    public static final class SPAN_LAYER {
        private static StringTag SPAN_LAYER_TAG = new StringTag("span.layer");

        private static final String DB_LAYER = "db";
        private static final String RPC_LAYER = "rpc";
        private static final String NOSQL_LAYER = "nosql";
        private static final String HTTP_LAYER = "http";

        public static void asDBAccess(Span span){
            SPAN_LAYER_TAG.set(span, DB_LAYER);
        }

        public static void asRPC(Span span){
            SPAN_LAYER_TAG.set(span, RPC_LAYER);
        }

        public static void asNoSQL(Span span){
            SPAN_LAYER_TAG.set(span, NOSQL_LAYER);
        }

        public static void asHttp(Span span){
            SPAN_LAYER_TAG.set(span, HTTP_LAYER);
        }

        public static String get(Span span){
            return SPAN_LAYER_TAG.get(span);
        }

        public static boolean isDBAccess(Span span){
            return DB_LAYER.equals(get(span));
        }

        public static boolean isRPC(Span span){
            return RPC_LAYER.equals(get(span));
        }

        public static boolean isNoSQL(Span span){
            return NOSQL_LAYER.equals(get(span));
        }

        public static boolean isHttp(Span span){
            return HTTP_LAYER.equals(get(span));
        }
    }

    /**
     *  COMPONENT is a low-cardinality identifier of the module, library, or package that is instrumented.
     *  Like dubbo/dubbox/motan
     */
    public static final StringTag COMPONENT  = new StringTag("component");

    /**
     * ERROR indicates whether a Span ended in an error state.
     */
    public static final BooleanTag ERROR = new BooleanTag("error");

    /**
     *  PEER_HOST records host address of the peer, maybe IPV4, IPV6 or hostname.
     */
    public static final StringTag PEER_HOST = new StringTag("peer.host");

    /**
     *  DB_URL records the url of the database access.
     */
    public static final StringTag DB_URL = new StringTag("db.url");

    /**
     *  DB_SQL records the sql statement of the database access.
     */
    public static final StringTag DB_SQL = new StringTag("db.statement");
}
