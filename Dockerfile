FROM openjdk:14-jdk-alpine

COPY target/keycloak-clojure.jar /keycloak-clojure.jar

ENTRYPOINT java -Djava.security.egd=file:/dev/./urandom -jar /keycloak-clojure.jar --infra-context /etc/keycloak/infra-context.edn --realm-config /etc/keycloak/realm-config.clj --login ${KEYCLOAK_LOGIN} --password ${KEYCLOAK_PASSWORD}
