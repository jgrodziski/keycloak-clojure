#!/usr/bin/env bash

docker-compose up -d
clojure -M:dev:test -m kaocha.runner "$@"
