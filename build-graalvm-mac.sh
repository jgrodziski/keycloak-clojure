#!/usr/bin/env bash

if [ -z "$GRAALVM_HOME" ]; then
    echo "Please set GRAALVM_HOME"
    exit 1
fi

"$GRAALVM_HOME/bin/gu" install native-image || true

export PATH=$GRAALVM_HOME/bin:$PATH

rm -rf classes/*
clj -e "(compile 'keycloak.starter)"
clj -M:uberdeps

args=( "-jar" "target/keycloak-clojure.jar" \
              "-H:Name=target/keycloak-clojure-starter-mac" \
              "-H:+ReportExceptionStackTraces" \
              "-H:Log=registerResource:" \
              "--report-unsupported-elements-at-runtime" \
              "--initialize-at-build-time" \
              "--initialize-at-run-time=java.lang.Math\\$RandomNumberGeneratorHolder,org.keycloak.adapters.rotation.JWKPublicKeyLocator,org.keycloak.adapters.KeycloakDeployment,vault.timer,org.jboss.resteasy.client.jaxrs.engines.factory.ApacheHttpClient4EngineFactory,vault.client.http.HTTPClient" \
              "--verbose" \
              "--no-fallback" \
              "--no-server" \
              "-J-Dclojure.compiler.direct-linking=true" \
              "-J-Xmx3g" )

$GRAALVM_HOME/bin/native-image "${args[@]}"
