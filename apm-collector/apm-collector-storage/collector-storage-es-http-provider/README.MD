# collector-storage-es-http-provider

This is is a plugin to the Elasticsearch storage component, which uses
HTTP by way of [Jest](https://github.com/searchbox-io/Jest). This currently supports 5.x version families.This module is copy from [collector-storage-es-provider](../apm-collector-storage/collector-storage-es-provider) and switch java api to rest client.

### Config

    #storage:
    #  elasticsearch-http:
    #    cluster_name: elasticsearch
    #    cluster_nodes: 127.0.0.1:9200
    #    index_shards_number: 2
    #    ssl : false
    #    index_replicas_number: 0
    #    username : admin
    #    password : admin

    
    * `cluster_name`: elasticsearch cluster name. Defaults to "elasticsearch".
    * `cluster_nodes`: A comma separated list of elasticsearch base urls to connect to ex. host:9200.
                  Defaults to "localhost:9200".
                  
                  Client can sniff other nodes cross elasticsearch,so you can only give one node. 
    * `ES_TIMEOUT`: Controls the connect, read and write socket timeouts (in milliseconds) for
                    Elasticsearch Api. Defaults to 10000 (10 seconds)
    * `ES_INDEX`: The index prefix to use when generating daily index names. Defaults to zipkin.
    * `ES_DATE_SEPARATOR`: The date separator to use when generating daily index names. Defaults to '-'.
    * `index_shards_number`: The number of shards to split the index into. Each shard and its replicas
                         are assigned to a machine in the cluster. Increasing the number of shards
                         and machines in the cluster will improve read and write performance. Number
                         of shards cannot be changed for existing indices, but new daily indices
                         will pick up changes to the setting. Defaults to 5.
    * `ssl`: Https settings.Default to "false".When ssl set to true,the url to elasticsearch will change the schema to "https".
    * `username` and `password`: Elasticsearch basic authentication, which defaults to empty string.
                                       Use when X-Pack security (formerly Shield) is in place.


