# Group Parameterized Endpoints
In most cases, the endpoint should be detected automatically through the language agents, service mesh observability solution,
or configuration of meter system.

There are some special cases, especially when people use REST style URI, the application codes put the parameter in the endpoint name,
such as putting order id in the URI, like `/prod/ORDER123` and `/prod/ORDER123`. But logically, people expect they could
have an endpoint name like `prod/{order-id}`. This is the feature of parameterized endpoint grouping designed for.

If the incoming endpoint name hit the rules, SkyWalking will grouping the endpoint by rules.

SkyWalking provides 2 ways to support endpoint grouping:
1. Endpoint name grouping by OpenAPI definitions.
2. Endpoint name grouping by custom configuration.

The 2 grouping features can work together in sequence.
## Endpoint name grouping by OpenAPI definitions
The OpenAPI definitions are the documents based on The [OpenAPI Specification (OAS)](https://github.com/OAI/OpenAPI-Specification) which used to define a standard, language-agnostic interface for HTTP APIs.
SkyWalking now support `OAS v2.0+)`, could parse the documents `(yaml)` and build the grouping rules from them automatically.


### How to use
1. Add some `Specification Extensions` for SkyWalking in the OpenAPI definition documents:<br />
   \${METHOD} is a reserved placeholder which represent HTTP method eg. `POST/GET...` <br />
   \${PATH} is a reserved placeholder which represent the path eg. `/products/{id}`.

   | Extension Name | Required | Description | Default Value |
      |-----|-----|-----|-----|
   | x-sw-service-name | true | The service name which these endpoints belong | |
   | x-sw-endpoint-name-match-rule | false | The rule use to match the endpoint.| \${METHOD}:\${PATH} |
   | x-sw-endpoint-name-format | false | The endpoint name after grouping.| \${METHOD}:\${PATH} |

   These extensions are under `OpenAPI Object`.
   We highly recommend using the default config, the custom config would be considered as part of the match rules (regex pattern).
   We provide some cases in `org.apache.skywalking.oap.server.core.config.group.openapi.EndpointGroupingRuleReader4OpenapiTest`, you could validate your custom config as well.

1. Put the OpenAPI definition documents into folder `openapi-definitions`, SkyWalking could read all documents or documents in subfolders from it，so you can organize these documents by yourself. For example:
  ```
├── openapi-definitions
│   ├── serviceA-api-v1
│   │   ├── customerAPI-v1.yaml
│   │   └── productAPI-v1.yaml
│   └── serviceB-api-v2
│       └── productAPI-v2.yaml
```
3. Turn the feature on by set the `Core Module` configuration `${SW_CORE_ENABLE_ENDPOINT_NAME_GROUPING_BY_OPAENAPI:true}`
<br />

### Rules match priority 
We recommend designing the API path as clear as possible. If the API path is fuzzy and a endpoint name might match multiple paths, SkyWalking would follow the match priority to select one as below orders:
1. The exact path matched first. 
   Eg. `/products or /products/inventory`
2. The the path which has the less variables. 
   Eg. `/products/{var1}/{var2} and /products/{var1}/abc`, endpoint name `/products/123/abc` will match the second one.
3. If the paths have the same number of variables, match the longest path, and the vars are considered to be `1`.
   Eg. `/products/abc/{var1} and products/{var12345}/ef`, endpoint name `/products/abc/ef` will match the first one.
### Examples
If we have a OpenAPI definition doc `productAPI-v2.yaml` like this:
```yaml

openapi: 3.0.0

info:
  description: OpenAPI definition for SkyWalking test.
  version: v2
  title: Product API

tags:
  - name: product
    description: product
  - name: relatedProducts
    description: Related Products

paths:
  /products:
    get:
      tags:
        - product
      summary: Get all products list
      description: Get all products list.
      operationId: getProducts
      responses:
        "200":
          description: Success
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: "#/components/schemas/Product"
  /products/{region}/{country}:
    get:
      tags:
        - product
      summary: Get products regional
      description: Get products regional with the given id.
      operationId: getProductRegional
      parameters:
        - name: region
          in: path
          description: Products region
          required: true
          schema:
            type: string
        - name: country
          in: path
          description: Products country
          required: true
          schema:
            type: string
      responses:
        "200":
          description: successful operation
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/Product"
        "400":
          description: Invalid parameters supplied
  /products/{id}:
    get:
      tags:
        - product
      summary: Get product details
      description: Get product details with the given id.
      operationId: getProduct
      parameters:
        - name: id
          in: path
          description: Product id
          required: true
          schema:
            type: integer
            format: int64
      responses:
        "200":
          description: successful operation
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ProductDetails"
        "400":
          description: Invalid product id
    post:
      tags:
        - product
      summary: Update product details
      description: Update product details with the given id.
      operationId: updateProduct
      parameters:
        - name: id
          in: path
          description: Product id
          required: true
          schema:
            type: integer
            format: int64
        - name: name
          in: query
          description: Product name
          required: true
          schema:
            type: string
      responses:
        "200":
          description: successful operation
    delete:
      tags:
        - product
      summary: Delete product details
      description: Delete product details with the given id.
      operationId: deleteProduct
      parameters:
        - name: id
          in: path
          description: Product id
          required: true
          schema:
            type: integer
            format: int64
      responses:
        "200":
          description: successful operation
  /products/{id}/relatedProducts:
    get:
      tags:
        - relatedProducts
      summary: Get related products
      description: Get related products with the given product id.
      operationId: getRelatedProducts
      parameters:
        - name: id
          in: path
          description: Product id
          required: true
          schema:
            type: integer
            format: int64
      responses:
        "200":
          description: successful operation
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/RelatedProducts"
        "400":
          description: Invalid product id

components:
  schemas:
    Product:
      type: object
      description: Product id and name
      properties:
        id:
          type: integer
          format: int64
          description: Product id
        name:
          type: string
          description: Product name
      required:
        - id
        - name
    ProductDetails:
      type: object
      description: Product details
      properties:
        id:
          type: integer
          format: int64
          description: Product id
        name:
          type: string
          description: Product name
        description:
          type: string
          description: Product description
      required:
        - id
        - name
    RelatedProducts:
      type: object
      description: Related Products
      properties:
        id:
          type: integer
          format: int32
          description: Product id
        relatedProducts:
          type: array
          description: List of related products
          items:
            $ref: "#/components/schemas/Product"


```

Here give some scenario we might use:
1. Only set the `x-sw-service-name`, `x-sw-endpoint-name-match-rule` and `x-sw-endpoint-name-format` are default:
``` yaml
openapi: 3.0.0
x-sw-service-name: serviceB

info:
  description: OpenAPI definition for SkyWalking test.
  version: v2
  title: Product API
  ...
```
2. Set the `x-sw-service-name` , `x-sw-endpoint-name-match-rule` and `x-sw-endpoint-name-format` :
``` yaml
openapi: 3.0.0
x-sw-service-name: serviceB
x-sw-endpoint-name-match-rule: "<${METHOD}>:${PATH}"
x-sw-endpoint-name-format: "<${METHOD}>:${PATH}"

info:
  description: OpenAPI definition for SkyWalking test.
  version: v2
  title: Product API
  ...
```
3. Set the `x-sw-service-name` , `x-sw-endpoint-name-match-rule` and `x-sw-endpoint-name-format` :
``` yaml
openapi: 3.0.0
x-sw-service-name: serviceB
x-sw-endpoint-name-match-rule: "<${METHOD}>:${PATH}"
x-sw-endpoint-name-format: "<${METHOD}>:${PATH}"

info:
  description: OpenAPI definition for SkyWalking test.
  version: v2
  title: Product API
  ...
```

   | Incoming Endpiont | Incoming Service | x-sw-endpoint-name-match-rule | x-sw-endpoint-name-format | Matched | Grouping Result |
   |-----|-----|-----|-----|-----|-----|
   | GET:/products | serviceB | default | default | true | GET:/products |
   | GET:/products/123 | serviceB | default |default |  true | GET:/products{id} |
   | GET:/products/asia/cn | serviceB | default | default | true | GET:/products/{region}/{country} |
   | GET:/products/123/abc/efg | serviceB | default |default |  false | GET:/products/123/abc/efg | 
   | \<GET\>:/products/123 | serviceB | default | default | false | \<GET\>:/products/123|
   | GET:/products/123 | serviceC | default | default | false | GET:/products/123 |
   | \<GET\>:/products/123 | serviceB | \<\${METHOD}\>:\${PATH} | \<\${METHOD}>:\${PATH} | true | \<GET\>:/products/{id} |
   | GET:/products/123 | serviceB | default | ${PATH}:\<\${METHOD}\> | true | /products/{id}:\<GET\> |
   | /products/123:<GET> | serviceB | ${PATH}:\<\${METHOD}\> | default | true | \<GET\>:/products/{id} |
<br />




## Endpoint name grouping by custom configuration
Current, user could set up grouping rules through the static YAML file, named `endpoint-name-grouping.yml`,
or use [Dynamic Configuration](dynamic-config.md) to initial and update the endpoint grouping rule.

### Configuration Format
No matter in static local file or dynamic configuration value, they are sharing the same YAML format.

```yaml
grouping:
  # Endpoint of the service would follow the following rules
  - service-name: serviceA
    rules:
      # Logic name when the regex expression matched.
      - endpoint-name: /prod/{id}
        regex: \/prod\/.+
```