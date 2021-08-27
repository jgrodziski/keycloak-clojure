# Security Concepts

<!-- markdown-toc start - Don't edit this section. Run M-x markdown-toc-refresh-toc -->

- [Generic security concepts](#generic-security-concepts)
    - [Identification and Authentication](#identification-and-authentication)
    - [Authorization](#authorization)
    - [Confidentiality and integrity](#confidentiality-and-integrity)
- [OAuth2 / OpenId Connect (OIDC) concepts](#oauth2--openid-connect-oidc-concepts)
   - [Tokens](#tokens)
    - [Grant Types and Credentials](#grant-types-and-credentials)
    - [Differences between OAuth2 and OIDC](#differences-between-oauth2-and-oidc)
- [Keycloak concepts](#keycloak-concepts)

<!-- markdown-toc end -->


## Generic security concepts

The subject of security is very wide and here we only focus on identity, access management and authorization within application. See the following mindmap for a broader view of all the existing security topics. 
<img src="https://raw.githubusercontent.com/jgrodziski/keycloak-clojure/master/resources/security-topics.png" width="720" alt="Mindmap listing all security topics" />

### Identification and Authentication
*Identification* means knowing who is interacting with your application and so you need a repository of identities (the persons or systems that interact with your application). Those identities can be handled by keycloak itself or it can delegates to another "storage" of identity, that's called [User Storage Federation](https://www.keycloak.org/docs/latest/server_admin/index.html#_user-storage-federation)

So *Identification* deals with the way users of the application are identified: what’s the identifier? where are they stored? What’s the provisionning of the users?
Once a user is identified, *Authentication* mechanism assesses whether the person or system is really who’s she said to be. The mechanism to assess a user’s identity is called an authentication factor, we can combine several of them (multi-factor authentication). They are categorized with: 

- what we know (password)
- what we own (key(s) in software like SSH or [OTP authenticator](https://www.keycloak.org/docs/latest/server_admin/index.html#otp-policies) or hardware like RSA)
- what we are (biometrics like face recognition or fingerprint)
- what we’re able to do (Captcha)

### Authorization

Once the user is *identified* and *authenticated*, *authorization* mechanisms are concerned with « what can she do? on which data or resources?». There are broadly two granularity of authorization mechanisms:

- Role-based access control – RBAC - is a powerful way to decouple the organizational structure (user in groups) and the assigned roles.
- Access-Control-List – ACL – is more fine-grained way of asserted whether someone can access a particular object (file, data, etc.)


## OAuth2 / OpenId Connect (OIDC) concepts

The OAuth2 and OIDC concepts are defined below, with the specificities of an application composed of an SPA frontend and an API backend.
Caution: The names of -almost- the same concept are different between OAuth and OIDC, see the schema at the end of this section.

* **Resource**: the thing you want to protect, your API if you develop a backend for instance.
* **Resource Owner**: the owner of the identity, data and any actions that can be performed with the identity. if you develop an API, you'll typically include then extract the roles from the access token the backend will receive
* **Client**: The application that wants to access data or perform actions on behalf of the Resource Owner. If you develop an application with an SPA as frontend and backend with an API, both your frontend and backend will be client
* **Authorization Server**: The system that knows the Resource Owner. Here we're talking about Keycloak.
* **Resource Server**: The Application Programming Interface (API) or service the Client wants to use on behalf of the Resource Owner. The backend of your application
*  **OpenID provider (OP)**: The OpenID provider is an OAuth 2.0 authorization server which offers authentication as a service. It ensures the end user is authenticated and provides claims about the end user and the authentication event to the relying party. The identity provider provides the relying party information about the end user through identity tokens.
* **Redirect URI**: The URL the Authorization Server will redirect the Resource Owner back to after granting permission to the Client. This is sometimes referred to as the “Callback URL”. Once you have some permissions given by Keycloak as an Access Token (see below), Keycloak will redirect towards your frontend, so it's the URL where your frontend sits.
* **Response Type**: The type of information the Client expects to receive. The most common Response Type is code, where the Client expects an Authorization Code.
* **Scope**: These are the granular permissions the Client wants, such as access to data or to perform actions.
* **Consent**: The Authorization Server takes the Scopes the Client is requesting, and verifies with the Resource Owner whether or not they want to give the Client permission.
* **Client ID**: This ID is used to identify the Client with the Authorization Server. When you ask for an access token, you'll give the `client-id` on behalf of which you ask permissions. Say you have api client, you can create a specific client in Keycloak for that particular use case.
* **Client Secret**: This is a secret password that only the Client and Authorization Server know. This allows them to securely share information privately behind the scenes. This is typically for your backend that you materialize as a client. Keycloak expects your backend to be able to holds some confidentiality, therefore holding a secret to prove its identity with Keycloak. 
* **Authorization Code**: A short-lived temporary code the Client gives the Authorization Server in exchange for an Access Token used during authorization code grant.

The following schema represents the related OAuth and OIDC concepts.
<img src="https://raw.githubusercontent.com/jgrodziski/keycloak-clojure/master/resources/oauth-oidc-concepts.png" width="720" alt="OAuth and OpenID connect OIDC concepts" />


### Tokens 

* **Access Token**: An *Access Token * is the key the client use to communicate with the *Resource Server*. This is like a badge or key card that gives the *Client* permission to request data or perform actions with the *Resource Server* on your behalf. Gives permission to the client application to obtain end-user owned resources from a resource server. It is an opaque token that is validated by fetching user claims from userInfo endpoint.
* **Refresh Token**: *Refresh Token* are used to get a new *Access Token*. They are long lived and kept by the *Authorization Server* that can invalidate them in case of a disclosure. 
* **ID Token**: Similar to a ID card or passport, it contains many required attributes or claims about the user.

### Grant Types and Credentials

*Access Token* can be acquired through different *Grant Types* (aka. methods): 

- **Authorization code**: the client will go to the authorization server to get first an authorization code to exchange for an access token in a second time.
- **Implicit**: the client get directly an access token only (no refresh token) because of being insecure.
- **Resource owner credentials:** given a username and a password the authorization server returns an access token and a refresh token, for trusted client only. 
- **Client credentials:** using a client secret to get an access token, suitable for machine-to-machine authentication.
- **Refresh token:** Access tokens eventually expire; however some grants respond with a refresh token which enables the client to get a new access token without requiring the user to be redirected. Refresh Token is long lived and are stored on the authorization server (and so can be disabled) and are used to refresh the access token periodically.

We choose the Grant type depending on the level of trust we have with the client to handle authorization credentials.


### Differences between OAuth2 and OIDC

## Keycloak concepts

Keycloak comes with many concepts closely related to the OAuth ones:

* **Realm** is the core concept in Keycloak. A *realm* secures and manages security metadata for a set of users, applications and registered oauth clients. 
* A **Client** is a service, i.e. a runtime component talking to Keycloak, that is secured by a *realm*. Once your *realm* is created, you can create a *client*: e.g. web frontend code in a browser, mobile frontend code in a React Native app, API server, etc. You will often use *Client* for every Application secured by Keycloak. When a user browses an application's web site, the application can redirect the user agent to the Keycloak Server and request a login. Once a user is logged in, they can visit any other client (application) managed by the *realm* and not have to re-enter credentials. This also hold true for logging out. 
* **Roles** can also be defined at the *client* level and assigned to specific users. Depending on the *client* type, you may also be able to view and manage *user* *sessions* from the administration console.
* **Adapters** are keycloak librairies in different technologies used for *client* to communicate with the keycloak servers. Luckily thanks to Clojure and Clojurescript running on hosted platform, respectively the JVM and the JS engine, we can use the [Keycloak Java Adapter](https://www.keycloak.org/docs/latest/securing_apps/index.html#java-adapters) and the [Keycloak Javascript Adapter](https://www.keycloak.org/docs/latest/securing_apps/index.html#_javascript_adapter).

Brokering
Federation

[OpenId Connect terminology](http://openid.net/specs/openid-connect-core-1_0.html#Terminology) is implemented by keycloak.

