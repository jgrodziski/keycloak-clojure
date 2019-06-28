
![Keycloak plus Clojure](keycloak-plus-clojure.png)


**[Keycloak](http://www.keycloak.org) is an open source Identity and Access Management solution aimed at modern applications and services. It makes it easy to secure applications and services with little to no code. This library wrap the Keycloak Java Adapter and provide some utilities facilitating the integration.**

This repo was first an explanation of integrating Keycloak with Clojure, now I transform it to offer a library named `keycloak-clojure` to wrap the Keycloak Java Adapter and provide some utilities facilitating the integration. The initial explanation is now in the README of the `sample` directory.

[![Clojars Project](https://img.shields.io/clojars/v/keycloak-clojure.svg)](https://clojars.org/keycloak-clojure)

```clojure
keycloak-clojure {:mvn/version "1.3.6"}
```

Before going further be sure to read the [sample's README](sample) to understand the concepts Keycloak offers and the integration points needed to integrate it with your application backend and frontend. Of course the way Keycloak integrates with your application depends of the stack it uses.

# Keycloak 

## Concepts

*Realm* is the core concept in Keycloak. A *realm* secures and manages security metadata for a set of users, applications and registered oauth clients. 

A *client* is a service that is secured by a *realm*. Once your *realm* is created, you can create a *client* i.e. a runtime component talking to keycloak: web frontend code in a browser, mobile frontend code in a React Native app, API server, etc. You will often use *Client* for every Application secured by Keycloak. 

When a user browses an application's web site, the application can redirect the user agent to the Keycloak Server and request a login. Once a user is logged in, they can visit any other client (application) managed by the *realm* and not have to re-enter credentials. This also hold true for logging out. 

*Roles* can also be defined at the *client* level and assigned to specific users. Depending on the *client* type, you may also be able to view and manage *user* *sessions* from the administration console.

*Adapters* are keycloak librairies in different technologies used for *client* to communicate with the keycloak servers. Luckily thanks to Clojure and Clojurescript running on hosted platform, respectively the JVM and the JS engine, we can use the [Keycloak Java Adapter](https://www.keycloak.org/docs/latest/securing_apps/index.html#java-adapters) and the [Keycloak Jsvascript Adapter](https://www.keycloak.org/docs/latest/securing_apps/index.html#_javascript_adapter).

[OpenId Connect terminology](http://openid.net/specs/openid-connect-core-1_0.html#Terminology) is implemented by keycloak.

## Authorization Concepts

### Resource Server

Per OAuth2 terminology, a resource server is the server hosting the protected resources and capable of accepting and responding to protected resource requests.

Resource servers usually rely on some kind of information to decide whether access to a protected resource should be granted. For RESTful-based resource servers, that information is usually carried in a security token, typically sent as a bearer token along with every request to the server.

In Keycloak, any confidential client application can act as a resource server. This client’s resources and their respective scopes are protected and governed by a set of authorization policies.

### Resource

A resource is part of the assets of an application and the organization. It can be a set of one or more endpoints, a classic web resource such as an HTML page, and so on. In authorization policy terminology, a resource is the object being protected.

Every resource has a unique identifier that can represent a single resource or a set of resources. For instance, you can manage a Banking Account Resource that represents and defines a set of authorization policies for all banking accounts. But you can also have a different resource named Alice’s Banking Account, which represents a single resource owned by a single customer, which can have its own set of authorization policies.

## Installation

You can use the [JBoss Keycloak docker image](https://hub.docker.com/r/jboss/keycloak/) `docker pull jboss/keycloak:4.8.3.Final`

You'll need an SQL database for the storage, I choose postgresql. There is a lot of documentation out there to configure Keycloak and postgresql, just google it. I put them behind a dockerized nginx proxy that manages quite easily the certificates renewing and proxying of docker container (TLS is mandatory for Keycloak outside of a localhost deployment).
I use [nginx proxy](https://github.com/jwilder/nginx-proxy) with the [Letsencrypt nginx proxy companion](https://github.com/JrCs/docker-letsencrypt-nginx-proxy-companion) for the SSL support (SSL access is for me quite mandatory for keycloak...). It's quite easy to setup (just add some env variables to the docker container and that's it) and it works very well.

I put a script in `bin/start-keycloak-docker.sh` assuming a postgresql running on locahost/default port (better perf on my mac than starting a dockerized postgres) to automate that thing.

### Install Postgresql

```
brew install postgresql
```

Make sure postgresql starts along the machine booting process:

```
pg_ctl -D /usr/local/var/postgres start && brew services start postgresql
```

### create the default database user for keycloak ###

```
createuser keycloak --createdb --pwprompt
```
when asked for a password, type `password`

### create the default database for keycloak ###

```
createdb keycloak -U keycloak 
```

### start Keycloak in a docker container ###

```
cd docker
./start-keycloak-docker.sh
```
now you can [connect on keycloak](http://localhost:8080) using "admin"/"password" to the "master" realm (the default one that Keycloak is using for connecting the "admin" user)


#### Create Application Realm ####

Now depending on the the usage the realm concept: 
* Multiple realms: one realm per tenant if you develop a SaaS application
* Single Realm: just one realm if your application is an internal enterprise application

#### Manual Realm Creation ####

You can create the realm manually. In the keycloak administration console create:
- [a realm](http://www.keycloak.org/docs/latest/getting_started/index.html#_create-realm)
- [in that realm, a client](http://www.keycloak.org/docs/latest/getting_started/index.html#creating-and-registering-the-client)
- in that realm, [a test user](http://www.keycloak.org/docs/latest/getting_started/index.html#_create-new-user)

The client screen has an "installation" tab that allows to grab the credentials secret for this client that will be part of the needed configuration.

##### Automatic Realm Creation #####

Add the `keycloak-clojure` dependency to your Clojure project: `[keycloak-clojure "0.1.5"]` or `keycloak-clojure {:mvn/version "0.1.5"}`.
Fire up a REPL, then:

```clojure
(ns keycloak.admin-test
  (:require [keycloak.admin :refer [create-realm!]]
            [keycloak.deployment :refer [keycloak-client client-conf]]))

;;create the admin keycloak client in "master" realm for client "admin-cli"
(def admin-client (keycloak-client (client-conf "master" "admin-cli" "http://localhost:8080/auth") "admin" "password"))

;;create our own
(create-realm! admin-client "myrealm")
```

# Keycloak interaction with a web frontend and an API backend #

The following schema describes the steps and the interactions between the browser, the keycloak server and the API server:



# Backend



## Installation

## Keycloak configuration

## Client

## Authentication and authorization usage

## Admin Usage (create Realm, Client, Role, User, etc.) 

## Sample integration with Yada

# Frontend

## Installation

## Usage

## Sample integration with Re-frame
