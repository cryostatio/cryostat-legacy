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

```bash
$ https -f :8181/api/v1/targets/localhost:9093/recordings recordingName=foo events=template=Pro
filing duration=30
HTTP/1.1 201 Created
content-encoding: gzip
content-length: 238
content-type: application/json
location: /foo

{
    "continuous": false,
    "downloadUrl": "https://localhost:8181/api/v1/targets/service:jmx:rmi:%2F%2F%2Fjndi%2Frmi:%2F%2Flocalhost:9093%2Fjmxrmi/recordings/foo",
    "duration": 30000,
    "id": 1,
    "maxAge": 0,
    "maxSize": 0,
    "name": "foo",
    "reportUrl": "https://localhost:8181/api/v1/targets/service:jmx:rmi:%2F%2F%2Fjndi%2Frmi:%2F%2Flocalhost:9093%2Fjmxrmi/reports/foo",
    "startTime": 1639515339870,
    "state": "RUNNING",
    "toDisk": true
}


$ echo -n save | https -v PATCH :8181/api/v1/targets/$(echo -n  "service:jmx:rmi:///jndi/rmi://cryostat:9093/jmxrmi" | jq -sRr @uri)/recordings/foo
PATCH /api/v1/targets/service%3Ajmx%3Armi%3A%2F%2F%2Fjndi%2Frmi%3A%2F%2Fcryostat%3A9093%2Fjmxrmi/recordings/foo HTTP/1.1
Accept: application/json, */*;q=0.5
Accept-Encoding: gzip, deflate
Connection: keep-alive
Content-Length: 4
Content-Type: application/json
Host: localhost:8181
User-Agent: HTTPie/2.6.0

save


HTTP/1.1 200 OK
content-encoding: gzip
content-length: 74
content-type: text/plain

es-andrewazor-demo-Main_foo_20211214T205546Z.jfr


$ https -v :8181/api/beta/graphql query=@graphql/targets-by-parent-query-4.graphql
POST /api/beta/graphql HTTP/1.1
Accept: application/json, */*;q=0.5
Accept-Encoding: gzip, deflate
Connection: keep-alive
Content-Length: 816
Content-Type: application/json
Host: localhost:8181
User-Agent: HTTPie/2.6.0

{
    "query": "query {\n    targetsDescendedFrom(nodes: [{ name: \"service:jmx:rmi:///jndi/rmi://cryostat:9093/jmxrmi\", nodeType: \"JVM\" }]) {\n        name\n        nodeType\n        target {\n            alias\n            serviceUri\n            labels\n            annotations {\n                platform\n                cryostat\n            }\n        }\n        recordings {\n            active {\n                name\n                downloadUrl\n                reportUrl\n                state\n                startTime\n                duration\n                continuous\n                toDisk\n                maxSize\n                maxAge\n            }\n            archived {\n                name\n                downloadUrl\n                reportUrl\n            }\n        }\n    }\n}\n\n"
}


HTTP/1.1 200 OK
content-encoding: gzip
content-length: 471
content-type: application/json

{
    "data": {
        "targetsDescendedFrom": [
            {
                "name": "service:jmx:rmi:///jndi/rmi://cryostat:9093/jmxrmi",
                "nodeType": "JVM",
                "recordings": {
                    "active": [
                        {
                            "continuous": false,
                            "downloadUrl": "https://localhost:8181/api/v1/targets/service:jmx:rmi:%2F%2F%2Fjndi%2Frmi:%2F%2Fcryostat:9093%2Fjmxrmi/recordings/foo",
                            "duration": 30000,
                            "maxAge": 0,
                            "maxSize": 0,
                            "name": "foo",
                            "reportUrl": "https://localhost:8181/api/v1/targets/service:jmx:rmi:%2F%2F%2Fjndi%2Frmi:%2F%2Fcryostat:9093%2Fjmxrmi/reports/foo",
                            "startTime": 1639515339870,
                            "state": "RUNNING",
                            "toDisk": true
                        }
                    ],
                    "archived": [
                        {
                            "downloadUrl": "https://localhost:8181/api/v1/recordings/es-andrewazor-demo-Main_foo_20211214T205413Z.jfr",
                            "name": "es-andrewazor-demo-Main_foo_20211214T205413Z.jfr",
                            "reportUrl": "https://localhost:8181/api/v1/reports/es-andrewazor-demo-Main_foo_20211214T205413Z.jfr"
                        }
                    ]
                },
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
