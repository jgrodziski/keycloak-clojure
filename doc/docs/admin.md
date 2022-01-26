# Administrative tasks

<!-- markdown-toc start - Don't edit this section. Run M-x markdown-toc-refresh-toc -->

- [Introduction](#introduction)
- [Create a keycloak client](#create-a-keycloak-client)
- [Create a realm](#create-a-realm)
- [Create clients](#create-clients)
- [Create realm roles](#create-realm-roles)
- [Create users](#create-users)
- [Assign roles to user](#assign-roles-to-user)
- [Create groups](#create-groups)
- [Declarative creation of Keycloak objects](#declarative-creation-of-keycloak-objects)
    - [Realm declaration](#realm-declaration)
    - [Clients declaration](#clients-declaration)
    - [Roles declaration](#roles-declaration)
    - [Groups declaration](#groups-declaration)
    - [Users declaration](#users-declaration)
- [Devops and Automated keycloak configuration](#devops-and-automated-keycloak-configuration)
    - [Declarative setup](#declarative-setup)
        - [Realm description sample](#realm-description-sample)
    - [REPL or Clojure setup](#repl-or-clojure-setup)
    - [Realm setup with `keycloak-clojure-starter` CLI](#realm-setup-with-keycloak-clojure-starter-cli)
        - [Infrastructure context](#infrastructure-context)
        - [Clojure CLI](#clojure-cli)
        - [Native CLI](#native-cli)
        - [Docker CLI](#docker-cli)
    - [Clients setup](#clients-setup)

<!-- markdown-toc end -->


## Introduction

Before using Keycloak you must create the necessary [Keycloak resources](/concepts/#keycloak-concepts) in the following order: 

1. A realm
2. Then clients, roles
3. and groups and users

You can create all theses resources through the [Keycloak administration console](http://localhost:8090) or keycloak-clojure brings you functions to do that easily and declaratively in the section [Declarative setup](#declarative-setup).

## Create a keycloak client

In every interaction with keycloak-clojure you must provide a keycloak client object that holds the server reference, password, etc.
The keycloak client is created with:

``` clojure

(require '[keycloak.deployment :as deployment 
           :refer [keycloak-client client-conf]])

(def kc-client
  (-> (client-conf {:auth-server-url "http://localhost:8090/auth" 
                    :realm "master"
                    :client-id  "admin-cli"})
      (keycloak-client "admin" "secretadmin")))
```

## Create a realm

``` clojure
(require '[keycloak.admin :as admin])
(admin/create-realm! kc-client "example-realm")

``` 


## Create clients

``` clojure
(admin/create-client! kc-client "example-realm" (client "myfrontend" true))
(admin/create-client! kc-client "example-realm" (client "mybackend" false))
```

## Create realm roles

``` clojure
(admin/create-role! kc-client "example-realm" "employee")
(admin/create-role! kc-client "example-realm" "manager")
```

## Create users

``` clojure

(admin/create-user! kc-client "example-realm" "user1" "pwd1")

;; The keycloak.user namespace contains function with more exhaustive parameters like:
(require '[keycloak.user :as user])

(user/create-or-update-user! kc-client "example-realm" {:username "bcarter" :first-name "Bob" :last-name "Carter" :password "abcdefg" :email "bcarter@example.com"} ["employee" "manager"] nil)
```

## Assign roles to user

``` clojure
(require '[keycloak.user :as user])

(user/add-realm-roles! kc-client "example-realm" "bcarter" ["manager"])

```

## Create groups

```clojure

(admin/create-group! kc-client "example-realm" "mygroup")
(admin/add-username-to-group-name! kc-client "example-realm" "mygroup" "bcarter")

```


## Declarative creation of Keycloak objects

Keycloak-clojure offers a declarative way to create all the Keycloak resources instead of invoking all the imperative functions.
The `init!` function to create all the resource sits in the namespace `keycloak.starter`.

The function expects the following top-level keys: `:realm`, `:clients`, `:roles`, `:groups`, `:users`.

Two additional keys provides a way to generate fake users, groups and roles: `:generated-users-by-group-and-role` and `:username-creator-fn`.

### Realm declaration

```clojure
{:name "electre"
 :themes {:internationalizationEnabled true
          :supportedLocales #{"en" "fr"}
          :defaultLocale "fr"
          :loginTheme "example-theme"
          :accountTheme "example-theme"
          :adminTheme nil
          :emailTheme "example-theme"}
 :login {:bruteForceProtected true
         :rememberMe true
         :resetPasswordAllowed true}
 :tokens {:ssoSessionIdleTimeoutRememberMe (Integer. (* 60 60 48)) ;2 days
          :ssoSessionMaxLifespanRememberMe (Integer. (* 60 60 48))}
 :smtp {:host "smtp.eu.mailgun.org"
        :port 587
        :from "admin@example.com" 
        :auth true
        :starttls true
        :replyTo "example"
        :user "postmaster@mail.example.com"
        :password ""}}
```

### Clients declaration

```clojure
{:clients [{:name          "api-client"
            :public?       true
            :redirect-uris ["https://api.example.com/*"]
            :root-url      "https://api.example.com"
            :base-url      "https://api.example.com"
            :web-origins   ["https://api.example.com"]}
           {:name          "myfrontend"
            :public?       true
            :redirect-uris ["https://www.example.com/*"]
            :root-url      "https://www.example.com"
            :base-url      "https://www.example.com"
            :web-origins   ["https://www.example.com"]}
           {:name          "mybackend"
            :public?       false
            :redirect-uris ["http://localhost:3449/*"]
            :web-origins   ["http://localhost:3449"]}]}

```

### Roles declaration

```clojure
{:roles  #{"employee" "manager" "admin" "org-admin" "group-admin" "api-consumer"}}
```

### Groups declaration

```clojure
{:groups [{:name "group1" :subgroups ["subgroup1" "subgroup2"]}]}
```

### Users declaration

```clojure
{:users [{:username "bcarter" :password "password" :first-name "Bob" :last-name  "Carter"
         :realm-roles ["employee" "manager"] :group "group1" :in-subgroups ["subgroup2"] :attributes {"myorg" ["ACME"]}}]}
```

# Devops and Automated keycloak configuration

Once installed, the keycloak server must be properly configured with realm(s) and clients. Automation is key, particularly in a cloud environment. You can have one Keycloak server per environment or likely share a server for non-prod environments and one for production. for the former setup, the server would have one realm and several clients corresponding to each environments that will be created and then deleted.

TODO: describe Vault integration with `init-client!` (client secret are exported automatically in Vault if the Vault config is present in `infra-context.edn`)

## Declarative setup

The data structure expected to setup a whole realm with clients, roles, groups and users is the following:

* `realm`: map with keys: `:name,` `:themes`, `:login`, `:smtp`, `:tokens`. `realm` with `name` is mandatory, all the other attributes are optional.
* `roles`: set with roles as string 
* `clients`: vector of map with keys: `:name`, `:redirect-uris`, `:base-url`, `:web-origins`, `:public?`, `:root-url`
* `groups`: vector of map with keys: `:name`, `:subgroups`: vector of map with keys: `:name` 
* `users`: vector of map with keys: `:email`, `:last-name`, `:group`, `:realm-roles` (vector of string), `:password`, `:username`, `:first-name`, `:attributes`, `:in-subgroups`
* `username-creator-fn`: function with parameters `[role group subgroup idx & opts]` that must return a string that will be used to automatically create user for testing purpose, in relation with the key `generated-users-by-group-and-role` that provide an integer with the number of fake users to create per group and role
* `generated-users-by-group-and-role`: Integer that denotes the number of fake users to create per group and role, see `username-creator-fn` key. If missing or zero no fake users are created.

### Realm description sample

```clojure

[{:realm {:name "example",
          :themes {:defaultLocale "fr",
                   :emailTheme "keycloak",
                   :internationalizationEnabled true,
                   :adminTheme nil,
                   :supportedLocales #{"en" "fr"},
                   :loginTheme "keycloak",
                   :accountTheme "keycloak"},
          :login {:resetPasswordAllowed true, :bruteForceProtected true, :rememberMe true},
          :smtp {:starttls true, :password "", :port 587, :auth true, :host "smtp.eu.mailgun.org", :replyTo "example", :from "admin@example.com", :user "postmaster@mg.example.com"},
          :tokens {:ssoSessionIdleTimeoutRememberMe 172800, :ssoSessionMaxLifespanRememberMe 172800}},
  :roles #{"org-admin" "example-admin" "group-admin" "api-consumer" "employee" "manager"},
  :clients [{:name "api-client",
             :redirect-uris ["https://myapp.staging.example.com/*"],
             :base-url "https://myapp.staging.example.com",
             :web-origins ["https://myapp.staging.example.com"],
             :public? true,
             :root-url "https://myapp.staging.example.com"}
            {:name "myfrontend",
             :redirect-uris ["https://myapp.staging.example.com/*"],
             :base-url "https://myapp.staging.example.com",
             :web-origins ["https://myapp.staging.example.com"],
             :public? true,
             :root-url "https://myapp.staging.example.com"}
            {:name "mybackend",
             :redirect-uris ["http://localhost:3449/*"],
             :web-origins ["http://localhost:3449"],
             :public? false}],
  :groups [{:name "test"} {:name "Example", :subgroups [{:name "IT"} {:name "Sales"} {:name "Logistics"}]}],
  :users [{:email "britt@hotmail.com", :last-name "Britt", :group "Example", :realm-roles ["employee" "manager" "example-admin" "org-admin" "group-admin" "api-consumer"], :password "s0w5roursg3i284", :username "britt", :first-name "James", :attributes {"org-ref" ["Example"]}, :in-subgroups ["IT"]}
          {:email "charlotte.peters@gmail.com", :last-name "Peters", :group "Example", :realm-roles ["employee" "manager" "example-admin" "org-admin" "group-admin" "api-consumer"], :password "7o9573867", :username "cpeters", :first-name "Charlotte", :attributes {"org-ref" ["Example"]}, :in-subgroups ["IT"]}
          {:email "skylar91@yahoo.com", :last-name "Nielsen", :group "Example", :realm-roles ["employee" "manager" "example-admin" "org-admin" "group-admin" "api-consumer"], :password "02e9nx6y6", :username "snielsen", :first-name "Skylar", :attributes {"org-ref" ["Example"]}, :in-subgroups ["IT"]}],
  :username-creator-fn (fn [role group subgroup idx & opts]
                          (str group "-" (subs (str role) 0 3) "-" idx)),
  :generated-users-by-group-and-role 2}]

```

## REPL or Clojure setup

The `keycloak.starter` namespace has the `init!` function that takes a data structure like the one described above or in `resources/real-config.edn`.
As Keycloak is an infrastructure component, the idea is that you could use the init features in two stages of your ops activity:

- **When you provision the Keycloak server**, at that moment you want to setup the realm and/or the groups/users
- **Everytime new versions of your application is deployed**, you want to setup some dedicated clients with their secrets exported in the Vault. Then the clients secrets are retrieved from the vault and given to the application for a proper starts.

In non production environment you could also want only one Keycloak server shared between different environments (staging, dev, etc.). The tools provided by Keycloak-Clojure should give you suppleness to adapt to your Ops setup and requirements.

The infrastructure configuration files contains all the connection data to the Keycloak and Vault server as well as metadata about the environment and applications Keycloak will secured. The idea is that Ops has the responsibility of domains URLs and deployed application and should passed that informations to the Keycloak init process. 

## Realm setup with `keycloak-clojure-starter` CLI

A native executable called `keycloak-clojure-starter`, also embedded in a [docker image called `keycloak-clojure-starter`](https://hub.docker.com/repository/docker/jgrodziski/keycloak-clojure-starter) for easy consumption in k8s context, is available. It takes a config file or direct configuration parameters of its environment: the keycloak and vault server, as well as optional metadata about the environment being created (environment, color, base-domain and applications). The optional metadata would then be fed to a second configuration file that will interpret the Clojure code in it in a [SCI](https://github.com/borkdude/sci) sandbox to get a realm configuration data structure.

The `keycloak-clojure-starter` CLI executable has the following arguments:

* `--auth-server-url` URL of the Keycloak Authentication Server
* `--login` username of a user with admin role in the master realm
* `--password` password of a user with admin role in the master realm
* `--environment` Name of the environment for which the init is done, has no impact but is passed during evaluation of the config file
* `--secret-export-dir` Path to a directory, if present clients secret will be exported in `keycloak-secrets.edn|json|yml` files for downstream usage.
* `--dry-run` A boolean flag that indicates that only the evaluation and print of the realm config (as --realm-config file) result will be executed, not applying the resulting Keycloak configuration. The boolean flag  recognizes "Y", "Yes", "On", "T", "True", and "1" as true values and "N", "No", "Off", "F", "False", and "0" as false values
* `--apply-deletions` A boolean flag that indicates that no deletions steps are applied, only additions and updates, then any existing objets in Keycloak would remains after invocation of the init. The boolean flag  recognizes "Y", "Yes", "On", "T", "True", and "1" as true values and "N", "No", "Off", "F", "False", and "0" as false values
* `--infra-context` Path to an EDN file. If the file is present it overrides the previous config parameters. The file contains the following keys, 
    - `:environment`: a string of the target environment, no impact but is passed during evaluation of the realm config file
    - `:color`: a string of a \"color\" for discriminating the target (can be omitted), no impact but is passed during evaluation of the realm config file
    - `:applications`: a vector of map with `:name`, `:version` and `clients-uris` keys, `clients-uris` being a vector of map with `client-id`, `:redirects`, `:base`, `:origins`, `:root` keys no impact but is passed during evaluation of the realm config file
    - `:keycloak`: a map with `:protocol`, `:host`, `:port`, `:login`, `:password`, `:base-domain`, `:secret-export-dir`
    - `:vault`: a map with the following entries:
      - `:vendor`: with value being `:hashicorp` or `:gcp-sm`
      - `:protocol`, `:host`, `:port`, `:token`, `:mount`, `:path` are keys that must be present if vendor is `:hashicorp`
      - `:project-id` and `:secret-id` if vendor is `:gcp-sm` also the `GOOGLE_APPLICATION_CREDENTIALS` environment variable must be properly defined and available at runtime "
* `--realm-config` A path to a clj file that is evaluated with SCI (https://github.com/borkdude/sci), the code must return a vector of map with a realm config (keys: realm, clients, roles see section [Declarative Setup](#declarative-setup))

### Infrastructure context 

For ease of use, the infrastructure context can be passed as a file to the starter function. It essentially contains the keycloak, optional vault and metadata parameters. The metadata parameters (`:environment`, `:color` and `applications`) are not used by the init function but are passed to the realm config clojure file that will be evaluated and the resulting data structure will be passed to the `keycloak.starter/init!` function.

Metadata are the keys:

* `environment`: a string describing the environment, eg. `staging`
* `color`: a string that further describe the environment with a specific infrastructure setup
* `applications`: A vector of map with keys: `:name`, `:version`, and `clients-uris` a vector of map with `client-id`, `:redirects`, `:base`, `:origins`, `:root` keys

Example of an `infra-context.edn` file 

```clojure
{:environment "staging"
 :color       "red"
 :applications {:name    "myapp"
                :version "1.2.3"
                :clients-uris [{:client-id "myapp-front"
                                :root       "https://myapp.staging.example.com"
                                :base       "/"
                                :redirects  ["https://myapp.staging.example.com", "https://myapp.staging.example.com/*"]
                                :origins    ["https://myapp.staging.example.com"]}
                               {:client-id "myapp-api"
                                :root       "https://api.myapp.staging.example.com"
                                :base       "/"
                                :redirects  ["https://api.myapp.staging.example.com", "https://api.myapp.staging.example.com/*"]
                                :origins    ["https://api.myapp.staging.example.com"]}]}
 :keycloak    {:protocol "http"
               :host     "host.docker.internal"
               :port     8090
               :login    "admin"
               :password "secretadmin"
               :secret-file-without-extension ".keycloak-secrets"}
 :secret-file {:name-without-extension ".keycloak-secrets"
               :path [:infra :keycloak]}
 :vault       {:protocol "http"
               :host     "host.docker.internal"
               :port     8200
               :token    "myroot"
               :mount    "secret"
               ;;%1$s is the environment, %2$s is the color, %3$s is the base-domain, %4$s is the client-id (so depends of your realm-config.clj code)
               :path     "/env/%1$s/keycloak/clients/%4$s"}}

```
### Clojure CLI

The clojure CLI is a traditional [clojure tools](https://clojure.org/guides/deps_and_cli) invocation of the `keycloak.starter` namespace main function.

```clojure
;;declare keycloak-clojure as a dependency in your deps.edn
keycloak.clojure {:mvn/version "1.14.1"}
```

```clojure
clj -m keycloak.starter --infra-context resources/keycloak-config.edn --realm-config resources/realm-config.clj --dry-run=Y
```

### Native CLI

The native CLI is currently broken due to the Vault integration that contains some imcompatible code for GraalVM (Thread creation).

### Docker CLI

You can use the [`keycloak-clojure-starter` docker image](https://hub.docker.com/r/jgrodziski/keycloak-clojure-starter) or rebuild the image on your own (see the `build.sh` script in the repo).

Then you use the image by running it and mounting the config files like:
```bash
docker run -d \
       --mount type=bind,source=$WORKDIR/resources,destination=/etc/keycloak \
       --mount type=bind,source=/Users/yourusername/keycloak-clojure/resources/keycloak-config.edn,destination=/etc/keycloak/keycloak-config.edn \
       --mount type=bind,source=/Users/yourusername/keycloak-clojure/resources/realm-config.clj,destination=/etc/keycloak/realm-config.clj \
       --env DRY_RUN=Yes \
       jgrodziski/keycloak-clojure-starter:1.22.1

```

## Clients setup




