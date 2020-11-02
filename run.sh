#!/bin/bash

WORKDIR=`pwd`

docker run  \
       --mount type=bind,source=$HOME,destination=/etc/keycloak \
       --mount type=bind,source=$WORKDIR/resources/infra-context-init.edn,destination=/etc/keycloak/infra-context.edn \
       --mount type=bind,source=$WORKDIR/resources/realm-config-init.clj,destination=/etc/keycloak/realm-config.clj \
       jgrodziski/keycloak-clojure-starter:latest

docker run  \
       --mount type=bind,source=$HOME,destination=/etc/keycloak \
       --mount type=bind,source=$WORKDIR/resources/infra-context-deploy.edn,destination=/etc/keycloak/infra-context.edn \
       --mount type=bind,source=$WORKDIR/resources/realm-config-clients.clj,destination=/etc/keycloak/realm-config.clj \
       jgrodziski/keycloak-clojure-starter:latest
