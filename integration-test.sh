#!/usr/bin/env bash

clojure -M:dev:test -m kaocha.runner "$@"
