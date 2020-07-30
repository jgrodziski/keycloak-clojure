# Security Concepts

[TOC]

## Generic

Identification and Authentication
Identification means knowing who is interacting with your application and so you need a repository of identities (the persons or systems that interact with your application). Those identities can be handled by keycloak itself or it can delegates


Authorization


Confidentiality and integrity

## OAuth2 / OpenId Connect (OIDC) concepts

Below are the OAuth2/OIDC concepts, with the specificities of an application composed of an SPA frontend and an API backend.

* **Resource**: the thing you want to protect.
    * your API if you develop a backend for instance
* **Resource Owner**: the owner of the identity, data and any actions that can be performed with the identity.
    * if you develop an API, you'll typically include then extract the roles from the access token the backend will receive
* **Client**: The application that wants to access data or perform actions on behalf of the Resource Owner.
    * if you develop an application with an SPA as frontend and backend with an API, both your frontend and backend will be client
* **Authorization Server**: The system that knows the Resource Owner 
    * Here we're talking about Keycloak
* **Resource Server**: The Application Programming Interface (API) or service the Client wants to use on behalf of the Resource Owner.
    * the backend of your application
*  **OpenID provider (OP)**: The OpenID provider is an OAuth 2.0 authorization server which offers authentication as a service. It ensures the end user is authenticated and provides claims about the end user and the authentication event to the relying party. The identity provider provides the relying party information about the end user through identity tokens.

Different types of tokens are exchanged between the participants to verify the identity or provide access permissions.
Tokens. Establishes a user’s identity during a transaction. These common token types are supported:

    Access token. Gives permission to the client application to obtain end-user owned resources from a resource server. It is an opaque token that is validated by fetching user claims from userInfo endpoint.

* **Redirect URI**: The URL the Authorization Server will redirect the Resource Owner back to after granting permission to the Client. This is sometimes referred to as the “Callback URL.”
    * once you have some permissions given by Keycloak as an Access Token (see below), Keycloak will redirect towards your frontend, so it's the URL where your frontend sits 
* **Response Type**: The type of information the Client expects to receive. The most common Response Type is code, where the Client expects an Authorization Code.
* **Scope**: These are the granular permissions the Client wants, such as access to data or to perform actions.
* **Consent**: The Authorization Server takes the Scopes the Client is requesting, and verifies with the Resource Owner whether or not they want to give the Client permission.
* **Client ID**: This ID is used to identify the Client with the Authorization Server.
  * when you ask for an access token, you'll give the client-id on behalf of which you ask permissions, say you have api client, you can create a specific client in Keycloak for that particular use case
* **Client Secret**: This is a secret password that only the Client and Authorization Server know. This allows them to securely share information privately behind the scenes.
  * this is typically for your backend that you materialize as a client. Keycloak expects your backend to be able to holds some confidentiality, therefore holding a secret to prove its identity with Keycloak. 
* **Authorization Code**: A short-lived temporary code the Client gives the Authorization Server in exchange for an Access Token.
* **Access Token**: The key the client will use to communicate with the Resource Server. This is like a badge or key card that gives the Client permission to request data or perform actions with the Resource Server on your behalf.
  * this is the data that will be exchanged between your frontend and your 
* **ID Token**: Similar to a ID card or passport, it contains many required attributes or claims about the user.


### Differences between OAuth2 and OIDC

## Keycloak concepts

Keycloak comes with many concepts:

* **Realm** is the core concept in Keycloak. A *realm* secures and manages security metadata for a set of users, applications and registered oauth clients. 
* A **client** is a service that is secured by a *realm*. Once your *realm* is created, you can create a *client* i.e. a runtime component talking to keycloak: web frontend code in a browser, mobile frontend code in a React Native app, API server, etc. You will often use *Client* for every Application secured by Keycloak. When a user browses an application's web site, the application can redirect the user agent to the Keycloak Server and request a login. Once a user is logged in, they can visit any other client (application) managed by the *realm* and not have to re-enter credentials. This also hold true for logging out. 

* **Roles** can also be defined at the *client* level and assigned to specific users. Depending on the *client* type, you may also be able to view and manage *user* *sessions* from the administration console.

* **Adapters** are keycloak librairies in different technologies used for *client* to communicate with the keycloak servers. Luckily thanks to Clojure and Clojurescript running on hosted platform, respectively the JVM and the JS engine, we can use the [Keycloak Java Adapter](https://www.keycloak.org/docs/latest/securing_apps/index.html#java-adapters) and the [Keycloak Jsvascript Adapter](https://www.keycloak.org/docs/latest/securing_apps/index.html#_javascript_adapter).

[OpenId Connect terminology](http://openid.net/specs/openid-connect-core-1_0.html#Terminology) is implemented by keycloak.

