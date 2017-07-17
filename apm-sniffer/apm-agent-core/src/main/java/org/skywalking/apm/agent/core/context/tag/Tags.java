package org.skywalking.apm.agent.core.context.tag;

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
    public static final StringTag STATUS_CODE = new StringTag("status_code");

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

    public static final class HTTP {
        public static final StringTag METHOD = new StringTag("http.method");
    }
}
