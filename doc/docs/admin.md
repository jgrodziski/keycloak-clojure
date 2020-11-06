# Administrative tasks

## Introduction

Before using Keycloak you must create the necessary resources in the following order: firstly you create a realm that will holds your clients, roles, groups and then users (See [the Keycloak concepts section](/concepts/#keycloak-concepts)).
You can create all theses resources through the [Keycloak administration console](http://localhost:8090) or keycloak-clojure brings you functions to do that easily that are detailed in the next sections.

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
(admin/create-client! kc-client "example-realm" "myfrontend")
(admin/create-client! kc-client "example-realm" "mybackend")
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


### Whole data structure for resources declaration

```clojure

(require '[keycloak.starter :as starter])
(starter/init! {:realm ...
                :clients ...
                :roles ... 
                :groups ... 
                :users ... 
                :generated-users-by-group-and-role 3
                :username-creator-fn (fn [role group subgroup i & opts] (str (str group) "-" (subs (str role) 0 3) "-" i))})

```
