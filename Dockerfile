FROM openjdk:14-jdk-alpine


COPY target/keycloak-clojure.jar /keycloak-clojure.jar


ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/keycloak-clojure.jar","--infra-config","/etc/keycloak/infra-config.edn","--realm-config","/etc/keycloak/realm-config.clj"]
