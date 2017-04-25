#!/bin/sh

check_pull_is_tagged() {
  if [ "${TRAVIS_TAG}" == "" ]; then
    return 1
  else
    echo "This build was started by the tag ${TRAVIS_TAG}, Prepare to push image"
    return 0
  fi
}

check_release_tag() {
    tag="${TRAVIS_TAG}"
    if [[ "$tag" =~ ^v[0-9.]*-[0-9]{4}$ ]]; then
	    return 0;
	else
	    echo "The provided tag ${tag} doesn't match that."
	    return 1;
    fi
}

push_image() {
  IMAGE_VERSION=`echo ${TRAVIS_TAG:1}`
  docker login -u="$DOCKER_USERNAME" -p="$DOCKER_PASSWORD"
  mvn clean package docker:build
  docker push skywalking/sky-walking-ui:latest
  docker push skywalking/sky-walking-ui:${IMAGE_VERSION}
}


if check_pull_is_tagged && check_release_tag; then
    push_image
    echo "Push is Done!"
fi