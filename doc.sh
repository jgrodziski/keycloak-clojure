#!/usr/bin/env bash

export AWS_REGION=us-east-1

clj -X:dev:doc keycloak.doc/-main
