#!/usr/bin/env bash

ARTIFACT_NAME=$(clj -M:artifact-name)
ARTIFACT_ID=$(echo "$ARTIFACT_NAME" | cut -f1)
ARTIFACT_VERSION=$(echo "$ARTIFACT_NAME" | cut -f2)
JAR_FILENAME="$ARTIFACT_ID-$ARTIFACT_VERSION.jar"

clj -X:uberjar

docker buildx create --driver docker-container --name kc-clj-builder --node kc-clj-node --platform linux/amd64,linux/arm64
docker buildx use kc-clj-builder
docker buildx inspect --bootstrap

docker buildx --builder kc-clj-builder build --platform linux/arm64,linux/amd64 -t jgrodziski/keycloak-clojure-starter:latest --push .

if [ $? -eq 0 ]; then
    echo "Successfully built \"keycloak-clojure\"'s docker image with JAR: jgrodziski/keycloak-clojure-starter:latest"
else
    echo "Fail to built \"keycloal-clojure\"'s docker image!"
    exit 1
fi

docker buildx --builder kc-clj-builder build --platform linux/arm64,linux/amd64 -t jgrodziski/keycloak-clojure-starter:$ARTIFACT_VERSION --push .

if [ $? -eq 0 ]; then
    echo "Successfully built \"keycloak-clojure\"'s docker image with JAR: jgrodziski/keycloak-clojure-starter:$ARTIFACT_VERSION"
else
    echo "Fail to built \"keycloal-clojure\"'s docker image!"
    exit 1
fi
