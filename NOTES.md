> Not sure exactly about all the details of your setup etc. However from the first look, if you use "response_type=id_token" , then Keycloak will return you just idToken, but not accessToken at all. 
> 
> If you want both idToken and accessToken, you need to use value "id_token token". 
> 
> So encoded parameter will be something like "response_type=id_token%20token"


https://stackoverflow.com/questions/49322417/obtain-id-token-with-keycloak



```javascript
 <script src="http://localhost:8080/auth/js/keycloak.js" type="text/javascript"></script>
<script type="text/javascript">
const keycloak = Keycloak({
    "realm": "yourRealm",
    "auth-server-url": "http://localhost:8080/auth",
    "ssl-required": "external",
    "resource": "yourRealm/keep it default",
    "public-client": true,
    "confidential-port": 0,
    "url": 'http://localhost:8080/auth',
    "clientId": 'yourClientId',
    "enable-cors": true
});
const loadData = () => {
    console.log(keycloak.subject);
    if (keycloak.idToken) {
        document.location.href = "?user="+keycloak.idTokenParsed.preferred_username;
        console.log('IDToken';
        console.log(keycloak.idTokenParsed.preferred_username);
        console.log(keycloak.idTokenParsed.email);
        console.log(keycloak.idTokenParsed.name);
        console.log(keycloak.idTokenParsed.given_name);
        console.log(keycloak.idTokenParsed.family_name);
    } else {
        keycloak.loadUserProfile(function() {
            console.log('Account Service');
            console.log(keycloak.profile.username);
            console.log(keycloak.profile.email);
            console.log(keycloak.profile.firstName + ' ' + keycloak.profile.lastName);
            console.log(keycloak.profile.firstName);
            console.log(keycloak.profile.lastName);
        }, function() {
            console.log('Failed to retrieve user details. Please enable claims or account role';
        });
    }
};
const loadFailure =  () => {
     console.log('Failed to load data.  Check console log';
};
const reloadData = () => {
    keycloak.updateToken(10)
            .success(loadData)
            .error(() => {
                console.log('Failed to load data.  User is logged out.');
            });
}
keycloak.init({ onLoad: 'login-required' }).success(reloadData);
</script>
```
