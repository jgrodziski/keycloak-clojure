#!/bin/bash

WORKDIR=`pwd`

docker run -d \
       --mount type=bind,source=$HOME,destination=/etc/keycloak \
       --mount type=bind,source=$WORKDIR/resources/keycloak-only-config.edn,destination=/etc/keycloak/infra-config.edn \
       --mount type=bind,source=$WORKDIR/resources/realm-init-config.clj,destination=/etc/keycloak/realm-config.clj \
       jgrodziski/keycloak-clojure-starter:latest

docker run -d \
       --mount type=bind,source=$HOME,destination=/etc/keycloak \
       --mount type=bind,source=$WORKDIR/resources/infra-config.edn,destination=/etc/keycloak/infra-config.edn \
       --mount type=bind,source=$WORKDIR/resources/realm-clients-config.clj,destination=/etc/keycloak/realm-config.clj \
       jgrodziski/keycloak-clojure-starter:latest
