# Docker Images

This folder contains the Dockerfile used to **build** our
[OAP Docker image](https://hub.docker.com/r/apache/skywalking-oap-server). If you want
to use the image, please check
[the user guide for OAP](../docs/en/setup/backend/backend-docker.md).

The web UI is no longer built from this repository — see the
[SkyWalking Horizon UI](https://github.com/apache/skywalking-horizon-ui) project,
whose images are published to
[`ghcr.io/apache/skywalking-horizon-ui`](https://github.com/apache/skywalking-horizon-ui/pkgs/container/skywalking-horizon-ui).

## Quickstart

You can use `Makefile` located at the root folder to build the OAP docker image
with the current codebase.

```shell
make docker
# OR skip the tests
make docker SKIP_TEST=true
```

It not only contains the process of building a docker image but also includes all the required steps, for instance, init
workspace, build artifact from scratch. It builds a single OAP image.

```shell
docker image ls | grep skywalking
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
`localhost:11800` and access the Horizon UI at <http://localhost:8080>
(default login `admin` / `admin`, configured in [docker/horizon.yaml](./horizon.yaml)).
The OAP admin host is exposed on `localhost:17128` (UI templates, runtime-rule
hot update, DSL debugging, status, inspect).
