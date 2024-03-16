# How to use the Docker images

## Start the storage, OAP and Booster UI with docker-compose

As a quick start, you can use our one-liner script to start ElasticSearch or
BanyanDB as the storage, OAP server and Booster UI, please make sure you have installed Docker.

**Linux, macOS, Windows (WSL)**
```shell
bash <(curl -sSL https://skywalking.apache.org/quickstart-docker.sh) 
```

**Windows (Powershell)**
```powershell
(Invoke-WebRequest -Uri https://skywalking.apache.org/quickstart-docker.ps1 -UseBasicParsing).Content | Invoke-Expression
```

You will be prompted to choose the storage type, and then the script will start the backend cluster with the selected storage. 

To tear down the cluster, run the following command:

```shell
docker compose --project-name=skywalking-quickstart down
```
## Start a `standalone` container with `H2` storage

```shell
docker run --name oap --restart always -d apache/skywalking-oap-server:9.7.0
```

## Start a `standalone` container with BanyanDB as storage, whose address is `banyandb:17912`

```shell
docker run --name oap --restart always -d -e SW_STORAGE=banyandb -e SW_STORAGE_BANYANDB_TARGETS=banyandb:17912 apache/skywalking-oap-server:9.7.0
```

## Start a `standalone` container with ElasticSearch 7 as storage, whose address is `elasticsearch:9200`

```shell
docker run --name oap --restart always -d -e SW_STORAGE=elasticsearch -e SW_STORAGE_ES_CLUSTER_NODES=elasticsearch:9200 apache/skywalking-oap-server:9.7.0
```

# Configuration

We could set up environment variables to configure this image. They are defined in [backend-setup](https://skywalking.apache.org/docs/main/next/en/setup/backend/backend-setup/).

# Extend image

If you intend to override or add config files in `/skywalking/config`, `/skywalking/ext-config` is the location for you to put extra files.
The files with the same name will be overridden; otherwise, they will be added to `/skywalking/config`.

If you want to add more libs/jars into the classpath of OAP, for example, new metrics for OAL. These jars can be mounted into `/skywalking/ext-libs`, then
`entrypoint` bash will append them into the classpath. Notice, you can't override an existing jar in classpath.
