#!/usr/bin/env bash

RELEASE_LEVEL=$1
MODULE_NAME=${PWD##*/}
echo "Release \"$MODULE_NAME\" with level '$RELEASE_LEVEL'"
tag=$(clj -Arelease $RELEASE_LEVEL --spit --output-dir . --formats json,clj --namespace keycloak.meta)

if [ $? -eq 0 ]; then
    echo "Successfully released \"$MODULE_NAME\" to $tag"
else
    echo "Fail to release \"$MODULE_NAME\"!"
    exit $?
fi

####################################################
# build jar                                        #
####################################################
source ./build.sh

####################################################
#                                                  #
#     Clojars uploading stuff (easier with Maven)  #
#                                                  #
####################################################

clj -Mspit
ARTIFACT_ID=$(cat src/keycloak/meta.json | jq -r '."module-name"')
ARTIFACT_VERSION=$(cat src/keycloak/meta.json | jq -r '."version"')
ARTIFACT_TAG=$(cat src/keycloak/meta.json | jq -r '."tag"')
JAR_FILENAME="$ARTIFACT_ID-$ARTIFACT_VERSION.jar"

if [[ $ARTIFACT_TAG =~ v(.+) ]]; then
    newversion=${BASH_REMATCH[1]}
else
    echo "unable to parse tag $tag"
    exit 1
fi
mvn versions:set -DnewVersion=$newversion  2>&1 > /dev/null

if [ $? -eq 0 ]; then
    echo "Successfully set new version of \"$MODULE_NAME\"'s pom to $newversion"
else
    echo "Fail to set new version of \"$MODULE_NAME\"'s pom!"
    exit $?
fi

# mvn deploy 2>&1 > /dev/null

mvn org.apache.maven.plugins:maven-deploy-plugin:3.0.0-M1:deploy-file \
    -Durl=https://clojars.org/repo \
    -DrepositoryId=clojars \
    -Dfile=target/$JAR_FILENAME \
    -DpomFile=pom.xml \
    -Dclassifier=


if [ $? -eq 0 ]; then
    echo "Successfully deployed \"$MODULE_NAME\" version $newversion to clojars"
else
    echo "Fail to deploy \"$MODULE_NAME\" to clojars!"
    exit $?
fi

docker push jgrodziski/keycloak-clojure-starter:latest
if [ $? -eq 0 ]; then
    echo "Successfully pushed jgrodziski/keycloak-clojure-starter:latest to docker hub"
else
    echo "Fail to push docker image to docker hub!"
    exit $?
fi

docker push jgrodziski/keycloak-clojure-starter:$newversion

if [ $? -eq 0 ]; then
    echo "Successfully pushed jgrodziski/keycloak-clojure-starter:$newversion to docker hub"
else
    echo "Fail to push docker image to docker hub!"
    exit $?
fi
