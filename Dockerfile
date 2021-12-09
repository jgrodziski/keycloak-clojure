FROM openjdk:11.0.13-jre-bullseye

COPY target/keycloak-clojure.jar /keycloak-clojure.jar

ENTRYPOINT java -Djava.security.egd=file:/dev/./urandom -jar /keycloak-clojure.jar --infra-context /etc/keycloak/infra-context.edn --realm-config /etc/keycloak/realm-config.clj --resources-dir /etc/keycloak
