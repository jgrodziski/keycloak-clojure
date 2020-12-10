#!/bin/bash

WORKDIR=`pwd`

docker run  \
       --mount type=bind,source=$home,destination=/etc/keycloak \
       --mount type=bind,source=$workdir/resources/infra-context-docker-init.edn,destination=/etc/keycloak/infra-context.edn \
       --mount type=bind,source=$workdir/resources/realm-config-init.clj,destination=/etc/keycloak/realm-config.clj \
       jgrodziski/keycloak-clojure-starter:latest

docker run  \
       --mount type=bind,source=$home,destination=/etc/keycloak \
       --mount type=bind,source=$workdir/resources/infra-context-docker-deploy.edn,destination=/etc/keycloak/infra-context.edn \
       --mount type=bind,source=$workdir/resources/realm-config-clients.clj,destination=/etc/keycloak/realm-config.clj \
       jgrodziski/keycloak-clojure-starter:latest
