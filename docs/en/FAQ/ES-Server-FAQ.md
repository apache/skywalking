# ElasticSearch 
Some new users may encounter the following issues:
* The performance of ElasticSearch is not as good as expected. For instance, the latest data cannot be accessed after some time.

Or 
* ERROR CODE 429.
```
    Suppressed: org.elasticsearch.client.ResponseException: method [POST], host [http://127.0.0.1:9200], URI [/service_instance_inventory/type/6_tcc-app-gateway-77b98ff6ff-crblx.cards_0_0/_update?refresh=true&timeout=1m], status line [HTTP/1.1 429 Too Many Requests]
{"error":{"root_cause":[{"type":"remote_transport_exception","reason":"[elasticsearch-0][10.16.9.130:9300][indices:data/write/update[s]]"}],"type":"es_rejected_execution_exception","reason":"rejected execution of org.elasticsearch.transport.TransportService$7@19a5cf02 on EsThreadPoolExecutor[name = elasticsearch-0/write, queue capacity = 200, org.elasticsearch.common.util.concurrent.EsThreadPoolExecutor@389297ad[Running, pool size = 2, active threads = 2, queued tasks = 200, completed tasks = 147611]]"},"status":429}
        at org.elasticsearch.client.RestClient$SyncResponseListener.get(RestClient.java:705) ~[elasticsearch-rest-client-6.3.2.jar:6.3.2]
        at org.elasticsearch.client.RestClient.performRequest(RestClient.java:235) ~[elasticsearch-rest-client-6.3.2.jar:6.3.2]
        at org.elasticsearch.client.RestClient.performRequest(RestClient.java:198) ~[elasticsearch-rest-client-6.3.2.jar:6.3.2]
        at org.elasticsearch.client.RestHighLevelClient.performRequest(RestHighLevelClient.java:522) ~[elasticsearch
```

You could add the following config to `elasticsearch.yml`, and set the value based on your environment variable.
```yml
# In the case of tracing, consider setting a value higher than this.
thread_pool.index.queue_size: 1000
thread_pool.write.queue_size: 1000

# When you face query error at trace page, remember to check this.
index.max_result_window: 1000000
```

For more information, see ElasticSearch's official documentation.
