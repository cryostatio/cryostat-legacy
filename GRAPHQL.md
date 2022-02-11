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

`graphql/` contains some sample queries which can be used as in the following
example queries. The first query is a standard API v2 requst to create a custom
target. The second query is a standard API v1 request to list all known targets.
The third query is a GraphQL query listing all known targets and all fields of
those targets, except without querying for the recordings belonging to the
target.

```bash
$ https -f :8181/api/v2/targets alias=foo connectUrl=localhost:0
HTTP/1.1 200 OK
content-encoding: gzip
content-length: 156
content-type: application/json

{
    "data": {
        "result": {
            "alias": "foo",
            "annotations": {
                "cryostat": {},
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
```

```bash
$ https :8181/api/v1/targets
HTTP/1.1 200 OK
content-encoding: gzip
content-length: 223
content-type: application/json

[
    {
        "alias": "foo",
        "annotations": {
            "cryostat": {},
            "platform": {}
        },
        "connectUrl": "localhost:0",
        "labels": {}
    },
    {
        "alias": "org.codehaus.plexus.classworlds.launcher.Launcher",
        "annotations": {
            "cryostat": {
                "HOST": "vengeance",
                "JAVA_MAIN": "org.codehaus.plexus.classworlds.launcher.Launcher",
                "PORT": "9091"
            },
            "platform": {}
        },
        "connectUrl": "service:jmx:rmi:///jndi/rmi://vengeance:9091/jmxrmi",
        "labels": {}
    }
]
```

```bash
$ https -v :8181/api/beta/graphql query=@graphql/target-nodes-query.graphql
POST /api/beta/graphql HTTP/1.1
Accept: application/json, */*;q=0.5
Accept-Encoding: gzip, deflate
Connection: keep-alive
Content-Length: 171
Content-Type: application/json
Host: localhost:8181
User-Agent: HTTPie/2.6.0

{
    "query": "query {\n    targetNodes {\n        name\n        nodeType\n        labels\n        target {\n            alias\n            serviceUri\n        }\n    }\n}\n"
}


HTTP/1.1 200 OK
content-encoding: gzip
content-length: 220
content-type: application/json

{
    "data": {
        "targetNodes": [
            {
                "labels": {},
                "name": "localhost:0",
                "nodeType": "CUSTOM_TARGET",
                "target": {
                    "alias": "foo",
                    "serviceUri": "localhost:0"
                }
            },
            {
                "labels": {},
                "name": "service:jmx:rmi:///jndi/rmi://vengeance:9091/jmxrmi",
                "nodeType": "JVM",
                "target": {
                    "alias": "org.codehaus.plexus.classworlds.launcher.Launcher",
                    "serviceUri": "service:jmx:rmi:///jndi/rmi://vengeance:9091/jmxrmi"
                }
            }
        ]
    }
}```

### `GET /api/beta/graphiql/*`

Serves a GraphQL "query IDE" that can be used for testing out writing queries
and seeing the responses served for those queries by `POST /api/beta/graphql`.
Note the `/*` in the path - to open this in your browser while running using
`run.sh`/`smoketest.sh`, go to `https://localhost:8181/api/beta/graphiql/`. The
trailing slash is significant.
