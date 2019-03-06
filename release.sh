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

mvn deploy  2>&1 > /dev/null
if [ $? -eq 0 ]; then
    echo "Successfully deployed \"$MODULE_NAME\" version $newversion to clojars"
else
    echo "Fail to deploy \"$MODULE_NAME\" to clojars!"
    exit 1
fi

