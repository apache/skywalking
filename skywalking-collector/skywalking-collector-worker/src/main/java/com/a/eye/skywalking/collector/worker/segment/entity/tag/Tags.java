package com.a.eye.skywalking.collector.worker.segment.entity.tag;


import com.a.eye.skywalking.collector.worker.segment.entity.Span;

/**
 * The span tags are supported by sky-walking engine.
 * As default, all tags will be stored, but these ones have particular meanings.
 * <p>
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
     * <p>
     * e.g.
     * db=database;
     * rpc=Remote Procedure Call Framework, like motan, thift;
     * nosql=something like redis/memcache
     */
    public static final class SPAN_LAYER {
        private static StringTag SPAN_LAYER_TAG = new StringTag("span.layer");

        public static String get(Span span) {
            return SPAN_LAYER_TAG.get(span);
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
    public static final BooleanTag ERROR = new BooleanTag("error", false);

    /**
     * PEER_HOST records host address (ip:port, or ip1:port1,ip2:port2) of the peer, maybe IPV4, IPV6 or hostname.
     */
    public static final StringTag PEER_HOST = new StringTag("peer.host");

    /**
     * PEER_PORT records remote port of the peer
     */
    public static final IntTag PEER_PORT = new IntTag("peer.port");

    /**
     * PEERS records multiple host address and port of remote
     */
    public static final StringTag PEERS = new StringTag("peers");

    /**
     * DB_TYPE records database type, such as sql, redis, cassandra and so on.
     */
    public static final StringTag DB_TYPE = new StringTag("db.type");

    /**
     * DB_INSTANCE records database instance name.
     */
    public static final StringTag DB_INSTANCE = new StringTag("db.instance");

    /**
     * DB_STATEMENT records the sql statement of the database access.
     */
    public static final StringTag DB_STATEMENT = new StringTag("db.statement");
}
