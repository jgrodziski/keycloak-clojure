#!/usr/bin/env bash

docker-compose up -d
export GOOGLE_APPLICATION_CREDENTIALS=./resources/adixe-1168-fe1fc6bddbbf.json
clojure -M:dev:test -m kaocha.runner "$@"
