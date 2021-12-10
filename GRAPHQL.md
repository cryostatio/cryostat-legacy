# GraphQL notes

This is a temporary notes dump while GraphQL work is ongoing.

## API Endpoints

### `[GET|POST] /api/beta/graphql`

Accepts `GET` or `POST` requests to perform GraphQL queries. See:
- https://www.graphql-java.com/tutorials/getting-started-with-spring-boot/
- https://graphql.org/learn/serving-over-http/
for some more info.

src/main/java/io/cryostat/net/web/http/api/beta/graph/GraphModule.java
contains the mock data that can be queried.

```bash
$ https :8181/api/beta/graphql query=@sample-query.graphql
HTTP/1.1 200 OK
content-encoding: gzip
content-length: 105
content-type: application/json

{
    "data": {
        "bookById": {
            "author": {
                "firstName": "Joanne",
                "lastName": "Rowling"
            },
            "pageCount": 223
        }
    }
}
```

### `GET /api/beta/graphiql/*`

Serves a GraphQL "query IDE" that can be used for testing out writing queries
and seeing the responses served for those queries by `POST /api/beta/graphql`.
Note the `/*` in the path - to open this in your browser while running using
`run.sh`/`smoketest.sh`, go to `https://localhost:8181/api/beta/graphiql/`. The
trailing slash is significant.
