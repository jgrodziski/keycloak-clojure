# Change Log
All notable changes to this project will be documented in this file. 
This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

# [1.30.2] - 2023-09-12

- [CVE-2023-2976](https://github.com/advisories/GHSA-7g45-4rm6-3mm3)
- [CVE-2022-1471](https://github.com/advisories/GHSA-mjmj-j48q-9wg2)

# [1.30.1] - 2023-08-03

- this change resolves the following vulnerability: [CVE-2023-2976](https://github.com/advisories/GHSA-7g45-4rm6-3mm3)

# [1.30.0] - 2023-02-27

- [Issue 51](https://github.com/jgrodziski/keycloak-clojure/issues/51)

# [1.29.1] - 2023-02-09

- this change resolves the following vulnerabilities: [CVE-2022-3171](https://nvd.nist.gov/vuln/detail/CVE-2022-3171)

# [1.29.0] - 2023-02-09

- This change resolves the following vulnerabilities: [CVE 2022-41854](https://nvd.nist.gov/vuln/detail/CVE-2022-41854)
- Bump Keycloak client libs to Keycloak's version `20.0.3`

# [1.28.7] - 2022-09-02

- This change resolves the following vulnerabilities: CVE-2020-25633 and CVE-2020-25647

# [1.28.6] - 2022-09-02

- Snakeyaml dependency had a CVE that they fixed, clj-yaml pulled it in with this newest release. https://nvd.nist.gov/vuln/detail/CVE-2022-25857

# [1.28.5] - 2022-07-12

- Refactoring of documentation for better presentation in cljdoc, need a patch on clojar...

# [1.28.4] - 2022-07-09

- [Issue 46](https://github.com/jgrodziski/keycloak-clojure/issues/46) - Refactor the keycloak.deployment/extract to include all the properties from JsonWebToken and IDToken

# [1.28.3] - 2022-06-22

- [Issue 45](https://github.com/jgrodziski/keycloak-clojure/issues/45) - Add a path parameter for building the Keycloak URL during starter init

# [1.28.2] - 2022-06-16

- Fix the naming of the `keycloak.user/add-required-actions!` function

# [1.28.1] - 2022-06-16

- [Issue 44](https://github.com/jgrodziski/keycloak-clojure/issues/44) - Add a specific function to add required action(s) to a user

# [1.28.0] - 2022-06-16

- [Issue 42](https://github.com/jgrodziski/keycloak-clojure/issues/42) - Add group's realm role assignment
- [Issue 43](https://github.com/jgrodziski/keycloak-clojure/issues/43) - Fix jackson-core dep missing in some setup

# [1.27.0] - 2022-05-11

- [Issue 41](https://github.com/jgrodziski/keycloak-clojure/issues/41) - Bump to Keycloak 18.0.0

# [1.26.0] - 2022-04-14

- [Issue 40](https://github.com/jgrodziski/keycloak-clojure/issues/40) - Retry mechanism with exponential backoff
- Bump Keycloak libs to version `16.1.1`

# [1.25.3] - 2022-03-15

- Upgrade SCI dependency org 
- Add token store feature (for client using a token) and near-expiration? predicate in `keycloak.authn` ns

# [1.25.2] - 2022-03-10

- Fix `keycloak.authn/authenticate` function by adding content-type 

# [1.25.1] - 2022-03-10

- Better reporting and logging when applying any reconciliation plan

# [1.25.0] - 2022-03-10

- Better performance for users reconciliation plan (avoid unnecessary process and add parallelization when groups are retrieved for every users in standalone requests) 
- [Issue #38](https://github.com/jgrodziski/keycloak-clojure/issues/38) Add `send-verification-email` and `execute-actions-email` functions in `keycloak.user` ns

# [1.24.2] - 2022-02-24

- Change HTTP configuration: pool size increase to 8, connect timeout of 4 seconds and read timeout of 20 seconds

# [1.24.1] - 2022-02-24

- Remove confusing output when applying a step from a reconciliation plan (particularly deletions that eventually are not applied...)

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

