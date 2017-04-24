#!/bin/sh

check_pull_is_release() {
  if [ "${TRAVIS_TAG}" = "" ]; then
    return 1
  else
    echo "[Pushing] pushing docker image of ${TRAVIS_TAG}."
    return 0
  fi

}

push_collector_image() {
  docker login -u="$DOCKER_USERNAME" -p="$DOCKER_PASSWORD"
  mvn clean package docker:build
  docker push skywalking/skywalking-collector:latest
  docker push skywalking/skywalking-collector:${TRAVIS_TAG}
}


if check_pull_is_release; then
    push_collector_image
    echo "[Pushing] push Done!"
fi