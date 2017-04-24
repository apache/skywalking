#!/bin/sh

check_pull_is_tagged() {
  if [ "${TRAVIS_TAG}" = "" ]; then
    return 1
  else
    echo "Push the collector image of ${TRAVIS_TAG} version."
    return 0
  fi

}

push_collector_image() {
  docker login -u="$DOCKER_USERNAME" -p="$DOCKER_PASSWORD"
  mvn clean package docker:build
  docker push skywalking/skywalking-collector:latest
  docker push skywalking/skywalking-collector:${TRAVIS_TAG}
}


if check_pull_is_tagged; then
    push_collector_image
    echo "Push is Done!"
fi