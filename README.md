# validating-wiremock

WireMock itself does not support Json schema validation in stand-alone mode. 
This tool simply packages WireMock and Atlassian's swagger-request-validator in a fat jar, and adds a command line option
to WireMock stand-alone runner to point to an OpenAPI (Swagger) definition file. 

With this setup, WireMock will return a 500 if a specific interaction violated the supplied schema. 

## Usage

Run WireMock stand-alone as usual (refer to its documentation). To validate interactions, use the command line option
--openapi-file with a Swagger 2 or OpenAPI 3 file. 

For example:

`Java -jar validating-wiremock.jar --openapi-file=./swagger-file.yaml`

Would validate interactions with WireMock against `./swagger-file.yaml` and return status 500 in case the interaction 
is not aligned with the definition file.

Note that WireMock can also act as a reverse proxy or ingress, which means you can use validating-wiremock as 
an API validation middleware. This is accomplished via WireMock's `proxyBaseUrl` constructs.

Here at Virgin Gates, we use it as an ingress to a docker compose network of services. This allows 
our front-end and mobile engineers to constantly validate their interactions with a microservice backend in 
their local environment. With this tool and setup, our engineers have the freedom to either (1) mock a service response, or 
(2) pass the request to the actual service, all while validating the request/response pair against our api yaml file.

