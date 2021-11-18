FROM openjdk:17-slim-bullseye

COPY target/keycloak-clojure.jar /keycloak-clojure.jar

ENTRYPOINT java -Djava.security.egd=file:/dev/./urandom -jar /keycloak-clojure.jar --infra-context /etc/keycloak/infra-context.edn --realm-config /etc/keycloak/realm-config.clj
