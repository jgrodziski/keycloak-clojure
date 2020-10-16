#!/usr/bin/env bash

ARTIFACT_NAME=$(clj -A:artifact-name)
ARTIFACT_ID=$(echo "$ARTIFACT_NAME" | cut -f1)
ARTIFACT_VERSION=$(echo "$ARTIFACT_NAME" | cut -f2)
JAR_FILENAME="$ARTIFACT_ID-$ARTIFACT_VERSION.jar"

echo -e "Build \"keycloak-clojure\" jar: target/$JAR_FILENAME"

source build-graalvm-linux.sh

docker build . -t jgrodziski/keycloak-clojure-starter:latest

docker build -f DockerfileNative . -t jgrodziski/keycloak-clojure-starter-native:latest

if [ $? -eq 0 ]; then
    echo "Successfully built \"keycloak-clojure\"'s docker image with native executable: jgrodziski/keycloak-clojure-starter:latest"
else
    echo "Fail to built \"keycloal-clojure\"'s docker image!"
    exit 1
fi

docker build . -t jgrodziski/keycloak-clojure-starter:$ARTIFACT_VERSION

docker build -f DockerfileNative . -t jgrodziski/keycloak-clojure-starter-native:$ARTIFACT_VERSION

if [ $? -eq 0 ]; then
    echo "Successfully built \"keycloak-clojure\"'s docker image with native executable: jgrodziski/keycloak-clojure-starter:$ARTIFACT_VERSION"
else
    echo "Fail to built \"keycloal-clojure\"'s docker image!"
    exit 1
fi

