#!/usr/bin/env bash


RELEASE_LEVEL=$1
MODULE_NAME=${PWD##*/}
echo "Release \"$MODULE_NAME\" with level '$RELEASE_LEVEL'"
tag=$(clj -Arelease $RELEASE_LEVEL)

if [ $? -eq 0 ]; then
    echo "Successfully released \"$MODULE_NAME\" to $tag"
else
    echo "Fail to release \"$MODULE_NAME\"!"
    exit 1
fi


####################################################
#                                                  #
#     Clojars uploading stuff (easier with Maven)  #
#                                                  #
####################################################

if [[ $tag =~ v(.+) ]]; then
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
    exit 1
fi

# mvn deploy 2>&1 > /dev/null

ARTIFACT_NAME=$(clj -A:artifact-name)
ARTIFACT_ID=$(echo "$ARTIFACT_NAME" | cut -f1)
ARTIFACT_VERSION=$(echo "$ARTIFACT_NAME" | cut -f2)
JAR_FILENAME="$ARTIFACT_ID-$ARTIFACT_VERSION.jar"

mvn org.apache.maven.plugins:maven-deploy-plugin:3.0.0-M1:deploy-file \
    -Durl=https://clojars.org/repo \
    -DrepositoryId=clojars \
    -Dfile=target/$JAR_FILENAME \
    -DpomFile=pom.xml \
    -Dclassifier=jar

if [ $? -eq 0 ]; then
    echo "Successfully deployed \"$MODULE_NAME\" version $newversion to clojars"
else
    echo "Fail to deploy \"$MODULE_NAME\" to clojars!"
    exit 1
fi

