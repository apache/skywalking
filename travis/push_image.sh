#!/bin/sh

check_pull_is_tagged() {
  if [ "${TRAVIS_TAG}" == "" ]; then
    return 1
  else
    return 0
  fi
}

check_branch_is_master(){
    if [ "${TRAVIS_BRANCH}" == "master" ]; then
        return 0;
    else
        return 1;
    fi
}

push_collector_image() {
  docker login -u="$DOCKER_USERNAME" -p="$DOCKER_PASSWORD"
  mvn clean package docker:build
  docker push skywalking/sky-walking-ui:latest
  docker push skywalking/sky-walking-ui:${TRAVIS_TAG}
}


if check_pull_is_tagged && check_branch_is_master; then
    push_collector_image
    echo "Push is Done!"
fi