# collector-storage-es-http-provider

This is is a plugin to the Elasticsearch storage component, which uses
HTTP by way of [Jest](https://github.com/searchbox-io/Jest). This currently supports 5.x version families.This module is copy from [collector-storage-es-provider](../collector-storage-es-provider) and switch java api to rest client.

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
    * `index_shards_number`: The number of shards to split the index into. Each shard and its replicas
                         are assigned to a machine in the cluster. Increasing the number of shards
                         and machines in the cluster will improve read and write performance. Number
                         of shards cannot be changed for existing indices, but new daily indices
                         will pick up changes to the setting. Defaults to 5.
    * `index_replicas_number`: The number of shard's replicas. Replica can make index HA(high availability) in case a shard/node fails.  and scale out your search volume/throughput. Defaults to 1.
    * `ssl`: Https settings.Default to "false".When ssl set to true,the url to elasticsearch will change the schema to "https".
    * `username` and `password`: Elasticsearch basic authentication, which defaults to empty string.
                                       Use when X-Pack security (formerly Shield) is in place.


