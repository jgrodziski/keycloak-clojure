#!/bin/bash

# this script assume that `docker login` has been run and that the docker host has enough memory (like 8Go)
# beware under macOS you have to allocate more memory through the docker desktop VM (otherwise a error indicating not enough memory will be throw, error 137), the memory limit of the docker CLI is only a check made by docker to not exceed.

rm -rf classes/*
clj -e "(compile 'keycloak.starter)"
clj -M:uberdeps

docker build -f Dockerfile-GraalVMLinuxBuilder -t keycloak-clojure-graalvm-linux-builder:latest .

docker run -it \
       --mount type=bind,source=/Users/jeremiegrodziski/Dropbox/projects/keycloak-clojure/target/keycloak-clojure-starter-linux,destination=/keycloak-clojure-starter \
       --mount type=bind,source=/Users/jeremiegrodziski/Dropbox/projects/keycloak-clojure/target/keycloak-clojure.jar,destination=/keycloak-clojure.jar \
       --memory=8g \
       keycloak-clojure-graalvm-linux-builder:latest \
       native-image -jar /keycloak-clojure.jar \
              -H:Name=/keycloak-clojure-starter \
              -H:+ReportExceptionStackTraces \
              -H:Log=registerResource: \
              -H:+TraceClassInitialization \
              --report-unsupported-elements-at-runtime \
              --initialize-at-run-time=java.lang.Math\\$RandomNumberGeneratorHolder,org.keycloak.adapters.rotation.JWKPublicKeyLocator,org.keycloak.adapters.KeycloakDeployment,vault.timer,org.jboss.resteasy.client.jaxrs.engines.factory.ApacheHttpClient4EngineFactory,vault.client.http.HTTPClient \
              --verbose \
              --no-fallback \
              --no-server \
	            --static \
              -J-Dclojure.compiler.direct-linking=true \
              -J-Xmx8g \
              -J-Xms2g

