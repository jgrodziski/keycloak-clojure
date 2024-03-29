#!/usr/bin/env bash

RELEASE_LEVEL=$1
MODULE_NAME=${PWD##*/}

echo "Release \"$MODULE_NAME\" with level '$RELEASE_LEVEL'"
tag=$(clojure -Mrelease $RELEASE_LEVEL --spit --output-dir src --formats clj,json --namespace keycloak.meta)

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
source ./build-docker-images.sh

####################################################
#                                                  #
#     Clojars uploading stuff (easier with Maven)  #
#                                                  #
####################################################
ARTIFACT_ID=$(cat src/keycloak/meta.json | jq -r '."module-name"')
ARTIFACT_VERSION=$(cat src/keycloak/meta.json | jq -r '."version"')
ARTIFACT_TAG=$(cat src/keycloak/meta.json | jq -r '."tag"')
JAR_FILENAME="$ARTIFACT_ID-$ARTIFACT_VERSION.jar"

# deploy to clojar after docker buildx because asking the gpg passphrase cana be hidden with docker buildx...:-(
clj -X:deploy :artifact \"$(echo target/$JAR_FILENAME)\"
