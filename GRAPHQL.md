# GraphQL notes

This is a temporary notes dump while GraphQL work is ongoing.

## API Endpoints

### `[GET|POST] /api/beta/graphql`

Accepts `GET` or `POST` requests to perform GraphQL queries. See:
- https://www.graphql-java.com/tutorials/getting-started-with-spring-boot/
- https://graphql.org/learn/serving-over-http/
for some more info.

src/main/java/io/cryostat/net/web/http/api/beta/graph/GraphModule.java
contains the bindings for the GraphQL engine to query custom targets.

```bash
$ https -f :8181/api/v2/targets alias=foo connectUrl=localhost:0 annotations.cryostat.HOST=localhost
HTTP/1.1 200 OK
content-encoding: gzip
content-length: 156
content-type: application/json

{
    "data": {
        "result": {
            "alias": "foo",
            "annotations": {
                "cryostat": {
                    "HOST": "localhost"
                },
                "platform": {}
            },
            "connectUrl": "localhost:0",
            "labels": {}
        }
    },
    "meta": {
        "status": "OK",
        "type": "application/json"
    }
}


$ https -v :8181/api/beta/graphql query=@targets-query.graphql
POST /api/beta/graphql HTTP/1.1
Accept: application/json, */*;q=0.5
Accept-Encoding: gzip, deflate
Connection: keep-alive
Content-Length: 140
Content-Type: application/json
Host: localhost:8181
User-Agent: HTTPie/2.6.0

{
    "query": "{\n  customTargets {\n    serviceUri\n    alias\n    labels\n    annotations {\n      cryostat\n      platform\n    }\n  }\n}\n"
}


HTTP/1.1 200 OK
content-encoding: gzip
content-length: 138
content-type: application/json

{
    "data": {
        "customTargets": [
            {
                "alias": "foo",
                "annotations": {
                    "cryostat": {
                        "HOST": "localhost"
                    },
                    "platform": {}
                },
                "labels": {},
                "serviceUri": "localhost:0"
            }
        ]
    }
}


$ https -v :8181/api/beta/graphql query=@targets-by-annotation-query.graphql
POST /api/beta/graphql HTTP/1.1
Accept: application/json, */*;q=0.5
Accept-Encoding: gzip, deflate
Connection: keep-alive
Content-Length: 178
Content-Type: application/json
Host: localhost:8181
User-Agent: HTTPie/2.6.0

{
    "query": "{\n  customTargetsWithAnnotation(annotation: \"HOST\") {\n    serviceUri\n    alias\n    labels\n    annotations {\n      cryostat\n      platform\n    }\n  }\n}\n\n"
}


HTTP/1.1 200 OK
content-encoding: gzip
content-length: 144
content-type: application/json

{
    "data": {
        "customTargetsWithAnnotation": [
            {
                "alias": "foo",
                "annotations": {
                    "cryostat": {
                        "HOST": "localhost"
                    },
                    "platform": {}
                },
                "labels": {},
                "serviceUri": "localhost:0"
            }
        ]
    }
}
```

### `GET /api/beta/graphiql/*`

Serves a GraphQL "query IDE" that can be used for testing out writing queries
and seeing the responses served for those queries by `POST /api/beta/graphql`.
Note the `/*` in the path - to open this in your browser while running using
`run.sh`/`smoketest.sh`, go to `https://localhost:8181/api/beta/graphiql/`. The
trailing slash is significant.
