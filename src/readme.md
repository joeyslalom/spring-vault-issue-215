# Spring Vault GitHub issue 215

This is a spring boot 2 project that illustrates https://github.com/spring-projects/spring-vault/issues/215

### Setup

1. Add PEM certificate to src/main/resources/ca-cert.pem to talk TLS with Vault
2. For token authentication, edit src/main/resources/bootstrap.properties and set two properties:
```
spring.cloud.vault.host=# TODO
spring.cloud.vault.token=# TODO
```