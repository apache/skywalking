#!/bin/sh

check_pull_is_tag_or_release() {
  if [ "${TRAVIS_TAG}" = "" ]; then
    return 1
  else
    echo "[Deploying] deploy tag ${TRAVIS_TAG}."
    return 0
  fi

}

deploy_collector_image() {
  docker login -u="$DOCKER_USERNAME" -p="$DOCKER_PASSWORD"
  mvn clean package docker:build
  docker push skywalking/skywalking-collector:latest
  docker push skywalking/skywalking-collector:${TRAVIS_TAG}
}


if check_pull_is_tag_or_release; then
    deploy_collector_image
    echo "[Deploying] deploy Done!"
fi