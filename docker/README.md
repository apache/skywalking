This folder contains Docker files for SkyWalking developers/committers to build images manually. If you want to start a SkyWalking backend server with docker-compose for your integration, please visit https://github.com/apache/skywalking-docker repository. 


## Quickstart
You can use `Makefile` located at the root folder to build a docker image with the current codebase.

```
make docker
```

It doesn't contain the process of building a docker image but also includes all the required steps, for instance, init workspace, build artifact from scratch. It builds two images, OAP, and UI.

```
docker image ls | grep skywalking
skywalking/ui                                   latest              a14db4e1d70d        6 minutes ago       800MB
skywalking/oap                                  latest              2a6084450b44        6 minutes ago       862MB
```


## Building variables

There're some environment variables to control image building.

### HUB

The hub of docker image. The default value is `skywalking`.

### TAG

The tag of docker image. The default value is `latest`.

### ES_VERSION

The elasticsearch version this image supports. The default value is `es6`, available values are `es6` and `es7`.


For example, if we want to build images with a hub `foo.io` and a tag `bar`, and it supports elasticsearch 7 at the same time.
We can issue the following commands.

```
export HUB=foo.io && export TAG=bar && export ES_VERSION=es7 && make docker
```

Let's check out the result:
```
docker image ls | grep foo.io
foo.io/ui                                       bar                 a14db4e1d70d        9 minutes ago       800MB
foo.io/oap                                      bar                 2a6084450b44        9 minutes ago       862MB
```

From the output, we can find out the building tool adopts the files stored in `oap-es7`.

## Running containers with docker-compose

We can start up backend cluster by docker-compose
```
cd docker
docker compose up
```
`docker/.env` file contains the default `TAG` and elasticsearch tag(`ES_TAG`).
