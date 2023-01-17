# How to use the Docker images

## Start a `standalone` container with `H2` storage

```shell
docker run --name oap --restart always -d apache/skywalking-oap-server:9.0.0
```

## Start a `standalone` container with ElasticSearch 7 as storage whose address is `elasticsearch:9200`

```shell
docker run --name oap --restart always -d -e SW_STORAGE=elasticsearch -e SW_STORAGE_ES_CLUSTER_NODES=elasticsearch:9200 apache/skywalking-oap-server:9.0.0
```

# Configuration

We could set up environment variables to configure this image. They are defined in [backend-setup](https://skywalking.apache.org/docs/main/next/en/setup/backend/backend-setup/).

# Extend image

If you intend to override or add config files in `/skywalking/config`, `/skywalking/ext-config` is the location for you to put extra files.
The files with the same name will be overridden; otherwise, they will be added to `/skywalking/config`.

If you want to add more libs/jars into the classpath of OAP, for example, new metrics for OAL. These jars can be mounted into `/skywalking/ext-libs`, then
`entrypoint` bash will append them into the classpath. Notice, you can't override an existing jar in classpath.
