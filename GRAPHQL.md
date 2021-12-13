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

`graphql/` contains some sample queries which can be used as follows:

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
$ https -v :8181/api/beta/graphql query=@graphql/targets-by-parent-query-1.graphql
POST /api/beta/graphql HTTP/1.1
Accept: application/json, */*;q=0.5
Accept-Encoding: gzip, deflate
Connection: keep-alive
Content-Length: 311
Content-Type: application/json
Host: localhost:8181
User-Agent: HTTPie/2.6.0

{
    "query": "{\n    targetsByParent(name: \"Universe\", nodeType: \"Universe\") {\n        name\n        nodeType\n        target {\n            alias\n            serviceUri\n            labels\n            annotations {\n                platform\n                cryostat\n            }\n        }\n    }\n}\n"
}


HTTP/1.1 200 OK
content-encoding: gzip
content-length: 354
content-type: application/json

{
    "data": {
        "targetsByParent": [
            {
                "name": "localhost:0",
                "nodeType": "CUSTOM_TARGET",
                "target": {
                    "alias": "foo",
                    "annotations": {
                        "cryostat": {},
                        "platform": {}
                    },
                    "labels": {},
                    "serviceUri": "localhost:0"
                }
            },
            {
                "name": "service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi",
                "nodeType": "JVM",
                "target": {
                    "alias": "io.cryostat.Cryostat",
                    "annotations": {
                        "cryostat": {
                            "HOST": "cryostat",
                            "JAVA_MAIN": "io.cryostat.Cryostat",
                            "PORT": "9091"
                        },
                        "platform": {}
                    },
                    "labels": {},
                    "serviceUri": "service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi"
                }
            },
            {
                "name": "service:jmx:rmi:///jndi/rmi://cryostat:9093/jmxrmi",
                "nodeType": "JVM",
                "target": {
                    "alias": "es.andrewazor.demo.Main",
                    "annotations": {
                        "cryostat": {
                            "HOST": "cryostat",
                            "JAVA_MAIN": "es.andrewazor.demo.Main",
                            "PORT": "9093"
                        },
                        "platform": {}
                    },
                    "labels": {},
                    "serviceUri": "service:jmx:rmi:///jndi/rmi://cryostat:9093/jmxrmi"
                }
            },
            {
                "name": "service:jmx:rmi:///jndi/rmi://cryostat:9094/jmxrmi",
                "nodeType": "JVM",
                "target": {
                    "alias": "es.andrewazor.demo.Main",
                    "annotations": {
                        "cryostat": {
                            "HOST": "cryostat",
                            "JAVA_MAIN": "es.andrewazor.demo.Main",
                            "PORT": "9094"
                        },
                        "platform": {}
                    },
                    "labels": {},
                    "serviceUri": "service:jmx:rmi:///jndi/rmi://cryostat:9094/jmxrmi"
                }
            },
            {
                "name": "service:jmx:rmi:///jndi/rmi://cryostat:9095/jmxrmi",
                "nodeType": "JVM",
                "target": {
                    "alias": "es.andrewazor.demo.Main",
                    "annotations": {
                        "cryostat": {
                            "HOST": "cryostat",
                            "JAVA_MAIN": "es.andrewazor.demo.Main",
                            "PORT": "9095"
                        },
                        "platform": {}
                    },
                    "labels": {},
                    "serviceUri": "service:jmx:rmi:///jndi/rmi://cryostat:9095/jmxrmi"
                }
            },
            {
                "name": "service:jmx:rmi:///jndi/rmi://cryostat:9096/jmxrmi",
                "nodeType": "JVM",
                "target": {
                    "alias": "/deployments/quarkus-run.jar",
                    "annotations": {
                        "cryostat": {
                            "HOST": "cryostat",
                            "JAVA_MAIN": "/deployments/quarkus-run.jar",
                            "PORT": "9096"
                        },
                        "platform": {}
                    },
                    "labels": {},
                    "serviceUri": "service:jmx:rmi:///jndi/rmi://cryostat:9096/jmxrmi"
                }
            }
        ]
    }
}
```

```bash
$ https -v :8181/api/beta/graphql query=@graphql/targets-by-parent-query-3.graphql
POST /api/beta/graphql HTTP/1.1
Accept: application/json, */*;q=0.5
Accept-Encoding: gzip, deflate
Connection: keep-alive
Content-Length: 350
Content-Type: application/json
Host: localhost:8181
User-Agent: HTTPie/2.6.0

{
    "query": "{\n    targetsByParent(name: \"service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi\", nodeType: \"JVM\") {\n        name\n        nodeType\n        target {\n            alias\n            serviceUri\n            labels\n            annotations {\n                platform\n                cryostat\n            }\n        }\n    }\n}\n\n"
}


HTTP/1.1 200 OK
content-encoding: gzip
content-length: 211
content-type: application/json

{
    "data": {
        "targetsByParent": [
            {
                "name": "service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi",
                "nodeType": "JVM",
                "target": {
                    "alias": "io.cryostat.Cryostat",
                    "annotations": {
                        "cryostat": {
                            "HOST": "cryostat",
                            "JAVA_MAIN": "io.cryostat.Cryostat",
                            "PORT": "9091"
                        },
                        "platform": {}
                    },
                    "labels": {},
                    "serviceUri": "service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi"
                }
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
