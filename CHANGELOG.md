# Change Log
All notable changes to this project will be documented in this file. 
This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

# [1.24.0] - 2022-02-24

- Add functions for generating passwords in ns `keycloak.user`

# [1.23.17]

- [Issue #37](https://github.com/jgrodziski/keycloak-clojure/issues/37) Fix for `keycloak.admin/regenerate-secret` failure

# [1.23.x] - 2022-01-21

- First release of the reconciliation behavior and usage in the starter init process

# [1.22.x] - 2022-01-19
- Add a `dry-run` option to CLI to only ontput the data structure but not applying it
- Fix a bug with hashicorp vault integration
- various bug fixes and patches for dry-run 

# [1.21.0] - 2022-01-06

- [Issue #35](https://github.com/jgrodziski/keycloak-clojure/issues/35): Bump keycloak-clojure to use Keycloak libs version 16.1.0
- [Issue #33](https://github.com/jgrodziski/keycloak-clojure/issues/33): Make the Docker image of keycloak-clojure-starter multiplatform (both linux/amd64 and linux/arm64 for Apple M1) 
- [Issue #34](https://github.com/jgrodziski/keycloak-clojure/issues/34): Add the client mappers as a parameter to starter
And a bug fix related to the attributes settings when updating an existing user.

# [1.18.0] - 2021-09-08

- [Issue #30](https://github.com/jgrodziski/keycloak-clojure/issues/30) - Add a new option `:user-admin` in `:realm` section of the starter input data structure

# [1.17.16] - 2021-09-03

- [Issue #29](https://github.com/jgrodziski/keycloak-clojure/issues/29) - Fix the keycloak.user/user-id behavior with now an exact match

# [1.17.15] - 2021-08-28

Add `.close` to Response object that were not closed. See https://github.com/jgrodziski/keycloak-clojure/issues/27

# [1.17.4] to [1.17.14]

All the patch between that two versions are for fixing the issues to make the lib properly integrating in cljdoc (mess with cli-matic and :git/url dep style).

# [1.17.3] - 2021-08-18

Fix NPE with the `user-for-update` function when no password is provided

# [1.15.0] - 2021-02-24

Add new functions in `keycloak.backend` namespace for verifying token in a Yada context or Ring request.

