# Keycloak Clojure 

[TOC]

You're here because you want to secure the application you develop. I assume your application is a typical javascript frontend with an API backend (Server-side rendering would not change this assumption that much). 

## Setup Keycloak

Keycloak comes vith [various way of running it](https://www.keycloak.org/getting-started): Bare server, Docker, Kubernetes, OpenShift. [Keycloak server installation and configuration documentation](https://www.keycloak.org/docs/latest/server_installation/index.html#guide-overview) is excellent.
Keycloak needs a database to run and offer several choices (Oracle, SQL Server, PostgreSQL, MySQL), by default it uses an H2 embedded database. I choose to run Keycloak with Postgres as it's easier to investigate data in a Postgres DB and it's now very frequent that I choose Postgres for my persistence needs.

### Docker-compose with Postgres

During development, I choose [Docker Compose](https://docs.docker.com/compose/) to run Postgres alongside Keycloak, the repository holds a `docker-compose.yml` file to easily starts a Keycloak standalone instance persisting data in a Postgres DB. 

To start Keycloak with Postgres:

* [install Docker Compose](https://docs.docker.com/compose/install/)
* Clone this repo: `git clone git@github.com:jgrodziski/keycloak-clojure.git` 
* and execute: `docker-compose up`







