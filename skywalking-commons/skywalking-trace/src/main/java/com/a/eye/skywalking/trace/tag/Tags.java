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
     *  SPAN_KIND represents the kind of span.
     *  e.g. db=database, rpc=Remote Procedure Call, nosql=something like redis/memcache
     */
    public static final class SPAN_KIND{
        private static StringTag SPAN_KIND_TAG = new StringTag("span.kind");

        public static void asDBAccess(Span span){
            SPAN_KIND_TAG.set(span, "db");
        }

        public static void asRPC(Span span){
            SPAN_KIND_TAG.set(span, "rpc");
        }

        public static void asNoSQL(Span span){
            SPAN_KIND_TAG.set(span, "nosql");
        }

        public static void asHttp(Span span){
            SPAN_KIND_TAG.set(span, "http");
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
     *  PEER_HOST_IPV4 records IPv4 host address of the peer.
     */
    public static final IntTag PEER_HOST_IPV4 = new IntTag("peer.ipv4");

    /**
     *  DB_URL records the url of the database access.
     */
    public static final StringTag DB_URL = new StringTag("db.url");

    /**
     *  DB_SQL records the sql statement of the database access.
     */
    public static final StringTag DB_SQL = new StringTag("db.statement");
}
