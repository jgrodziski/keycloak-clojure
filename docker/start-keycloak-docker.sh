#!/bin/bash

docker run -d \
       -e KEYCLOAK_ADMIN=admin \
       -e KEYCLOAK_ADMIN_PASSWORD=password \
       -e DB_ADDR=docker.for.mac.host.internal \
       -e DB_VENDOR=postgres \
       --name keycloak-dev \
       -p 127.0.0.1:9990:9990 \
       -p 127.0.0.1:8080:8080 \
       quay.io/keycloak/keycloak:24.0.5 start-dev
