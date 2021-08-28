# Keycloak Clojure

You're here because you want to secure the application you develop. Keycloak is an Identity and Access Management (IAM for short) tool that will handles the identities and their associated permissions that will use your application. Keycloak implements two related open protocols for dealing with authorization and authentication: [OAuth 2.0](https://oauth.net/2/) and [OpenID Connect](https://openid.net/connect/) (OIDC)
I assume your application is a typical javascript frontend with an API backend (Server-side rendering would not change this assumption that much).

The documentation has the following sections:

- **[Concepts](https://cljdoc.org/d/keycloak-clojure/keycloak-clojure/1.17.14/doc/security-concepts)**: Keycloak implements the concepts of the OAuth and OIDC protocols as well as its own concepts. This section explains these concepts and their purpose.
- **[Setup](https://cljdoc.org/d/keycloak-clojure/keycloak-clojure/1.17.14/doc/run-keycloak)**: this section exposes an easy way to install Keycloak on your development machine through Docker.
- **[Administration](https://cljdoc.org/d/keycloak-clojure/keycloak-clojure/1.17.14/doc/administrative-tasks)**: the administration tasks allows to create the different Keycloak resources used later when securing the application
- **[Frontend](https://cljdoc.org/d/keycloak-clojure/keycloak-clojure/1.17.14/doc/securing-a-frontend)**: this section explains how to secure a typical SPA frontend
- **[Backend](https://cljdoc.org/d/keycloak-clojure/keycloak-clojure/1.17.14/doc/securing-a-backend)**: this section explains how to secure an API backend.








