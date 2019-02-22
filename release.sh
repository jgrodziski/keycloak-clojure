#!/usr/bin/env bash

RELEASE_LEVEL=$1
tag=$(clj -Arelease $RELEASE_LEVEL)

if [[ $tag =~ v(.+)]]; then
    newversion=${BASH_REMATCH[1]}
else
    echo "unable to parse string $strname"
fi
mvn versions:set -DnewVersion=$newversion
