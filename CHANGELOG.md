# Change Log
All notable changes to this project will be documented in this file. 
This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

# [1.17.16] - 2021-09-03

- Issue #29 - Fix the keycloak.user/user-id behavior with now an exact match

# [1.17.15] - 2021-08-28

Add `.close` to Response object that were not closed. See https://github.com/jgrodziski/keycloak-clojure/issues/27

# [1.17.4] to [1.17.14]

All the patch between that two versions are for fixing the issues to make the lib properly integrating in cljdoc (mess with cli-matic and :git/url dep style).

# [1.17.3] - 2021-08-18

Fix NPE with the `user-for-update` function when no password is provided

# [1.15.0] - 2021-02-24

Add new functions in `keycloak.backend` namespace for verifying token in a Yada context or Ring request.

