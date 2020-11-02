(ns keycloak.vault.core
  "Core protocols for interacting with the Vault API."
  (:import
    java.net.URI))


;; ## Client Protocols

(defprotocol Client
  "General protocol for interacting with Vault."

  (authenticate!
    [client auth-type credentials]
    "Updates the client's internal state by authenticating with the given
    credentials. Possible arguments:

    - `:token \"...\"`
    - `:wrap-token \"...\"`
    - `:userpass {:username \"user\", :password \"hunter2\"}`
    - `:ldap {:username \"LDAP username\", :password \"hunter2\"}`
    - `:k8s {:jwt \"...\", :role \"...\"}`
    - `:app-id {:app \"foo-service-dev\", :user \"...\"}`
    - `:app-role {:role-id \"...\", :secret-id \"...\"}")

  (status
    [client]
    "Returns the health status of the Vault server."))


(defprotocol TokenManager
  "Token management interface supported by the \"token\" auth backend."

  (create-token!
    [client opts]
    "Creates a new token. With no arguments, this creates a child token that
    inherits the policies and settings from the current token. Options passed
    in the map may include:

    - `:id` The ID of the client token. Can only be specified by a root token.
      Otherwise, the token ID is a randomly generated UUID.
    - `:display-name` The display name of the token. Defaults to \"token\".
    - `:meta` Map of string metadata to attach to the token. This will appear
      in the audit logs.
    - `:no-parent` If true and set by a root caller, the token will not have
      the parent token of the caller. This creates a token with no parent.
    - `:policies` Set of policies to issue the token with. This must be a
      subset of the current token's policies, unless it is a root token.
    - `:no-default-policy` If true the default policy will not be contained in
      this token's policy set.
    - `:num-uses` The maximum uses for the given token. This can be used to
      create a one-time-token or limited use token. Defaults to 0, which has no
      limit to the number of uses.
    - `:renewable` Boolean indicating whether the token should be renewable.
    - `:ttl` The TTL period of the token, provided as \"1h\", where hour is the
      largest suffix. If not provided, the token is valid for the default lease
      TTL, or indefinitely if the root policy is used.
    - `:explicit-max-ttl` If set, the token will have an explicit max TTL set
      upon it. This maximum token TTL cannot be changed later, and unlike with
      normal tokens, updates to the system/mount max TTL value will have no
      effect at renewal time -- the token will never be able to be renewed or
      used past the value set at issue time.
    - `:wrap-ttl` Returns a wrapped response with a wrap-token valid for the
      given number of seconds.")

  (lookup-token
    [client]
    [client token]
    "Returns information about the given token, or the client token if not
    specified.")

  (renew-token
    [client]
    [client token]
    "Renews a lease associated with a token. Returns the renewed auth
    information. If `token` is not provided, this renews the client's own auth
    token and updates the internal client authentication state.

    This is used to prevent the expiration of a token, and the automatic
    revocation of it. Token renewal is possible only if there is a lease
    associated with it.")

  (revoke-token!
    [client]
    [client token]
    "Revokes a token and all child tokens. When the token is revoked, all
    secrets generated with it are also revoked.")

  (lookup-accessor
    [client token-accessor]
    "Fetch the properties of the token associated with the accessor, except the
    token ID. This is meant for purposes where there is no access to token ID
    but there is need to fetch the properties of a token.")

  (revoke-accessor!
    [client token-accessor]
    "Revoke the token associated with the accessor and all the child tokens.
    This is meant for purposes where there is no access to token ID but there
    is need to revoke a token and its children."))


(defprotocol LeaseManager
  "Lease management for dynamic secrets."

  (list-leases
    [client]
    "Lists the currently leased secrets this client knows about. Does not
    return secret data.")

  (renew-lease
    [client lease-id]
    "Renews the identified secret lease. Returns a map containing the
    extended `:lease-duration` and whether the lease is `:renewable`.")

  (revoke-lease!
    [client lease-id]
    "Revokes the identified secret lease. Returns nil on success.")

  (add-lease-watch
    [client watch-key path watch-fn]
    "Registers a new watch function which will be called whenever the lease for
    the secret at the given path is updated. The `watch-key` must be unique per
    client, and can be used to remove the watch later.

    The watch function will be called with the secret lease information
    whenever the lease-id changes. This can be because the secret was rotated,
    revoked, or expired. In the latter two cases, the function will be called
    with `nil` as the argument.")

  (remove-lease-watch
    [client watch-key]
    "Removes the watch function registered with the given key, if any."))


(defprotocol SecretEngine
  "Basic API for listing, reading, and writing secrets.

  **NOTE**: These are meant to be used as basic CRUD operations on Vault and is helpful for writing new Secret Engines.
  End users will likely want to use Secret Engines directly (see `vault.secrets`)"

  (list-secrets
    [client path]
    "Returns a vector of the secrets names located under a path.

     Params:
     - `client`: `vault.client`, A client that handles vault auth, leases, and basic CRUD ops
     - `path`: `String`, the path in vault of the secret you wish to list secrets at")

  (read-secret
    [client path opts]
    "Reads a resource from a path. Returns the full map of stored data if the resource exists, or throws an exception
    if not.

    Params:
    - `client`: `vault.client`, A client that handles vault auth, leases, and basic CRUD ops
    - `path`: `String`, the path in vault of the secret you wish to read
    - `opts`: `map`, Further optional read described below.

    Additional options may include:
    - `:not-found`
      If the requested path is not found, return this value instead of throwing
      an exception.
    - `:renew`
      Whether or not to renew this secret when the lease is near expiry.
    - `:rotate`
      Whether or not to rotate this secret when the lease is near expiry and
      cannot be renewed.
    - `:force-read`
      Force the secret to be read from the server even if there is a valid lease cached.
    - `:request-opts`
      Additional top level opts supported by clj-http")

  (write-secret!
    [client path data]
    "Writes secret data to a path. Returns a boolean indicating whether the write was successful.

    Params:
    - `client`: `vault.client`, A client that handles vault auth, leases, and basic CRUD ops
    - `path`: `String`, the path in vault of the secret you wish to write the secret to
    - `data`: `map`, the data you wish to write to the given path.")

  (delete-secret!
    [client path]
    "Removes secret data from a path. Returns a boolean indicating whether the deletion was successful.

    Params:
    - `client`: `vault.client`, A client that handles vault auth, leases, and basic CRUD ops
    - `path`: `String`, the path in vault of the secret you wish to delete the secret from"))


(defprotocol WrappingClient
  "Secret wrapping API for exchanging limited-use tokens for wrapped data."

  (wrap!
    [client data ttl]
    "Wraps the given user data in a single-use wrapping token. The wrap token
    will be valid for `ttl` seconds and a single use.")

  (unwrap!
    [client wrap-token]
    "Returns the original response wrapped by the given token."))


;; ## Client Construction

(defmulti new-client
  "Constructs a new Vault client from a URI by dispatching on the scheme. The
  client will be returned in an initialized but not started state."
  (fn dispatch
    [uri]
    (.getScheme (URI. uri))))


(defmethod new-client :default
  [uri]
  (throw (IllegalArgumentException.
           (str "Unsupported Vault client URI scheme: " (pr-str uri)))))


