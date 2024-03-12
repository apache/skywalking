# Docker Images

This folder contains Dockerfiles that are used to **build**
our [OAP Docker images](https://hub.docker.com/r/apache/skywalking-oap-server)
and [UI Docker image](https://hub.docker.com/r/apache/skywalking-ui). If you want to use the Docker images, please
check [the user guide for OAP](../docs/en/setup/backend/backend-docker.md)
and [the user guide for UI](../docs/en/setup/backend/ui-setup.md#start-with-docker-image).

## Quickstart

You can use `Makefile` located at the root folder to build a docker image with the current codebase.

```shell
make docker
# OR skip the tests
make docker SKIP_TEST=true
```

It not only contains the process of building a docker image but also includes all the required steps, for instance, init
workspace, build artifact from scratch. It builds two images, OAP, and UI.

```shell
docker image ls | grep skywalking
skywalking/ui                                   latest              a14db4e1d70d        6 minutes ago       800MB
skywalking/oap                                  latest              2a6084450b44        6 minutes ago       862MB
```

## Building variables

There are some environment variables to control image building.

### `CONTEXT`

The Docker build context path, under this path, there should be the distribution tar ball.

```shell
ls $CONTEXT
apache-skywalking-apm-bin.tar.gz
```

### `DIST`

The distribution tar ball name, for example, `apache-skywalking-apm-bin.tar.gz`.

### `HUB`

The hub of docker image. The default value is `skywalking`.

### `TAG`

The tag of docker image. The default value is `latest`.

## Running containers with docker-compose

We can start up backend cluster by docker-compose. There are two profiles with
different storage options that you can choose, `elasticsearch` and `banyandb`.

To start up the backend cluster with `elasticsearch` as the storage, run the
following command:

```shell
docker compose --profile elasticsearch up
```

To start up the backend cluster with `banyandb` as the storage, run the
following command:

```shell
docker compose --profile banyandb up
```

[docker/.env](./.env) file contains some configurations that you can customize,
such as the Docker image registry and tags.

After the services are up and running, you can send telemetry data to
localhost:11800 and access the UI at http://localhost:8080.
