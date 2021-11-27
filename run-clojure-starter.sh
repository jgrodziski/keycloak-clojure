#!/bin/bash

WORKDIR=`pwd`

clojure -m keycloak.starter --infra-context resources/infra-context-init.edn --realm-config resources/realm-config-init.clj --resources-dir resources
clojure -m keycloak.starter --infra-context resources/infra-context-deploy.edn --realm-config resources/realm-config-clients.clj --resources-dir resources
