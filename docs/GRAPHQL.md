# GraphQL

## API Endpoints

### `[GET|POST] /api/v2.2/graphql`

Accepts `GET` or `POST` requests to perform GraphQL queries. See:
- [Getting Started](https://www.graphql-java.com/tutorials/getting-started-with-spring-boot/)
- [Serving over HTTP](https://graphql.org/learn/serving-over-http/)
for some more info.

[`graphql/`](graphql/) contains some sample queries which can be used as in the following
example queries. The first query is a standard API v2 request to create a custom
target. The second query is a standard API v1 request to list all known targets.
The third query is a GraphQL query listing all known targets and all fields of
those targets, except without querying for the recordings belonging to the
target.

For more information about the available queries and mutations, check the
GraphiQL interface detailed below. Or, check the GraphQL schema definitions
directly:

- [queries.graphqls](https://github.com/cryostatio/cryostat/blob/main/src/main/resources/queries.graphqls)
- [types.graphqls](https://github.com/cryostatio/cryostat/blob/main/src/main/resources/types.graphqls)

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
                "HOST": "localhost",
                "JAVA_MAIN": "org.codehaus.plexus.classworlds.launcher.Launcher",
                "PORT": "9091"
            },
            "platform": {}
        },
        "connectUrl": "service:jmx:rmi:///jndi/rmi://localhost/jmxrmi",
        "labels": {}
    }
]
```

```bash
$ https -v :8181/api/v2.2/graphql query=@graphql/target-nodes-query.graphql
POST /api/v2.2/graphql HTTP/1.1
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
                "name": "service:jmx:rmi:///jndi/rmi://localhost/jmxrmi",
                "nodeType": "JVM",
                "target": {
                    "alias": "org.codehaus.plexus.classworlds.launcher.Launcher",
                    "serviceUri": "service:jmx:rmi:///jndi/rmi://localhost/jmxrmi"
                }
            }
        ]
    }
}
```

### `GET /api/v2.2/graphiql/*`

Serves a GraphQL "query IDE" ([GraphiQL](https://github.com/graphql/graphiql))
that can be used for testing out writing queries and seeing the responses served
for those queries by `POST /api/v2.2/graphql`.
Note the `/*` in the path - to open this in your browser while running using
`run.sh`/`smoketest.sh`, go to `https://localhost:8181/api/v2.2/graphiql/`. The
trailing slash is significant. This endpoint is only available and workable if
the environment variables `CRYOSTAT_DEV_MODE=true` and
`CRYOSTAT_AUTH_MANAGER=io.cryostat.net.NoopAuthManager` are set.
