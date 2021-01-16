
![Keycloak plus Clojure](../keycloak-plus-clojure.png)

- [Introduction](#introduction)
  - [Specifics of the choosen Clojure server and ClojureScript client libs](https://github.com/jgrodziski/keycloak-clojure#specifics-of-the-choosen-clojure-server-and-clojurescript-client-libs)
  - [Principles of User Authentication](https://github.com/jgrodziski/keycloak-clojure#principles-of-user-authentication)
- [Principles of User Authentication - Keycloak Core Concepts](https://github.com/jgrodziski/keycloak-clojure#principles-of-user-authentication---keycloak-core-concepts)
- [Installing and Configuring Keycloak](https://github.com/jgrodziski/keycloak-clojure#installing-and-configuring-keycloak)
- [Web Frontend SPA authentication](https://github.com/jgrodziski/keycloak-clojure#web-frontend-spa-authentication)
  - [Lib Installation](https://github.com/jgrodziski/keycloak-clojure#lib-installation)
  - [Initial Authentication Steps](https://github.com/jgrodziski/keycloak-clojure#initial-authentication-steps)
  - [Token Storage](https://github.com/jgrodziski/keycloak-clojure#token-storage)
  - [Token refreshing](https://github.com/jgrodziski/keycloak-clojure#token-refreshing)
- [API Server Authentication](https://github.com/jgrodziski/keycloak-clojure#api-server-authentication)

# Introduction

This repo was first an explanation of integrating Keycloak with Clojure, now I transform it to offer a library to wrap the Keycloak Java Adapter and provide some utilities facilitating the integration. The initial explanation is now in the README of the `sample` directory.

**This article explains the integration of [Keycloak](http://www.keycloak.org), an Identity and Access Management Server in a [Clojure](https://www.clojure.org) ecosystem.**

Identify, authenticate and get the user roles are a must-have for every application, and also administrate the user's metadata. The typical application architecture is now a web and mobile frontend talking to a server API (in a REST or GraphQL manner). By the way, Keycloak entered the [Thoughtworks TechRadar in november 2017](https://www.thoughtworks.com/radar/platforms/keycloak) in the Trial category.

## Specifics of the choosen Clojure server and ClojureScript client libs

The main libraries used in the sample app are [Reagent](http://reagent-project.github.io/)/[Re-Frame](https://github.com/Day8/re-frame), React native and [Yada](https://github.com/juxt/yada) for the API backend.
I'll try to clearly separate the inner details of making Keycloak work and those of the surrounding libraries. You should easily adapt the environment as I'll try to explain the reason behind every mechanisms. 

The impacting server libs are: 
- [Yada](https://github.com/juxt/yada)
- [Mount](https://github.com/tolitius/mount)

The impacting client libs are:
- [re-frame](https://github.com/Day8/re-frame)
- [Mount](https://github.com/tolitius/mount) on the client side

# Principles of User Authentication - Keycloak Core Concepts

*Realm* is the core concept in Keycloak. A *realm* secures and manages security metadata for a set of users, applications and registered oauth clients. 

A *client* is a service that is secured by a *realm*. Once your *realm* is created, you can create a *client* i.e. a runtime component talking to keycloak: web frontend code in a browser, mobile frontend code in a React Native app, API server, etc. You will often use Client for every Application secured by Keycloak. When a user browses an application's web site, the application can redirect the user agent to the Keycloak Server and request a login. Once a user is logged in, they can visit any other client (application) managed by the realm and not have to re-enter credentials. This also hold true for logging out. *Roles* can also be defined at the *client* level and assigned to specific users. Depending on the *client* type, you may also be able to view and manage *user* *sessions* from the administration console.

*Adapters* are keycloak librairies in different technologies used for *client* to communicate with the keycloak servers. Luckily thanks to Clojure and Clojurescript running on hosted platform, respectively the JVM and the JS engine, we can use the [Keycloak Java Adapter](http://www.keycloak.org/docs/3.2/securing_apps/topics/oidc/java/java-adapters.html) and the [Keycloak Jsvascript Adapter](http://www.keycloak.org/docs/3.2/securing_apps/topics/oidc/javascript-adapter.html).

[OpenId Connect terminology](http://openid.net/specs/openid-connect-core-1_0.html#Terminology) is implemented by keycloak.

# Installing and Configuring Keycloak
You can use the [JBoss Keycloak docker image](https://hub.docker.com/r/jboss/keycloak/) `docker pull  jboss/keycloak-postgres:3.3.0.Final`
You'll need an SQL database for the storage, here I choose postgresql. There is a lot of documentation out there to configure Keycloak and postgresql, just google it. I put them behind a dockerized nginx proxy that manages quite easily the certificates renewing and proxying of docker container.
I use [nginx proxy](https://github.com/jwilder/nginx-proxy) with the [Letsencrypt nginx proxy companion](https://github.com/JrCs/docker-letsencrypt-nginx-proxy-companion) for the SSL support (SSL access is for me quite mandatory for keycloak...). It's quite easy to setup (just add some env variables to the docker container and that's it) and it works very well.

Now connect to your keycloak administration console and create:
- [a realm](http://www.keycloak.org/docs/latest/getting_started/index.html#_create-realm)
- [in that realm, a client](http://www.keycloak.org/docs/latest/getting_started/index.html#creating-and-registering-the-client)
- in that realm, [a test user](http://www.keycloak.org/docs/latest/getting_started/index.html#_create-new-user)

The client screen has an "installation" tab that allows to grab the credentials secret for this client that will be part of the needed configuration.


# Web Frontend SPA authentication

## Lib installation
The keycloak javascript Adapter library is vanilla JS and does not implement the Google Closure contract, so the integration in the Leiningen cljsbuild should be in the form of a [foreign lib ](https://clojurescript.org/reference/packaging-foreign-deps) in your `project.clj`.

```clojure
  :cljsbuild
  {:builds
   [{:id           "dev"
     :source-paths ["src/cljs"]
     :figwheel     {:on-jsload "myapp.core/mount-root"}
     :compiler     {:main                 myapp.core
                    :output-to            "resources/public/js/compiled/app.js"
                    :output-dir           "resources/public/js/compiled/out"
                    :asset-path           "js/compiled/out"
                    :source-map-timestamp true
                    :preloads             [devtools.preload
                                           day8.re-frame.trace.preload
                                           re-frisk.preload]
                    :closure-defines      {"re_frame.trace.trace_enabled_QMARK_" true}
                    :external-config      {:devtools/config {:features-to-install :all}}
                    :externs ["lib/keycloak/keycloak-externs.js"]
                    :foreign-libs [{:file "lib/keycloak/keycloak.js"
                                    :provides ["keycloak"]}]
                    }}
```




# Mobile frontend authentication
TODO

# API server authentication


Copyright (c) 2017 [Jeremie Grodziski](https://www.grodziski.com) - MIT License
