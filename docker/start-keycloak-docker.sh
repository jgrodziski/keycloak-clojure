#!/bin/bash

docker rm keycloak-dev
docker run -d \
       -e KEYCLOAK_USER=admin \
       -e KEYCLOAK_PASSWORD=password \
       -e DB_ADDR=docker.for.mac.host.internal \
       -e DB_VENDOR=postgres \
       --name keycloak-dev \
       -p 127.0.0.1:9990:9990 \
       -p 127.0.0.1:8080:8080 \
       -v "$(pwd)"/keycloak/configuration:/opt/jboss/keycloak/standalone/configuration \
       -v "$(pwd)"/keycloak/themes:/opt/jboss/keycloak/themes \
       jboss/keycloak:6.0.1
