
# Install Postgresql

```
brew install postgresql
```

Make sure postgresql starts along the machine booting process:

```
pg_ctl -D /usr/local/var/postgres start && brew services start postgresql
```

## create a database user for keycloak
```
createuser keycloak --createdb --pwprompt
```
when asked for a password, type `password`

## create a database for keycloak
```
createdb keycloak -U keycloak 
```

# start Keycloak in a docker container

```
cd docker
./start-keycloak-dev.sh
```
now you can [connect on keycloak](http://localhost:8080)
