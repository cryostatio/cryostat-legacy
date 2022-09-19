# GraphQL

## API Endpoints

### `[GET|POST] /api/v2.2/graphql`

Accepts `GET` or `POST` requests to perform GraphQL queries. See:
- https://www.graphql-java.com/tutorials/getting-started-with-spring-boot/
- https://graphql.org/learn/serving-over-http/
for some more info.

src/main/java/io/cryostat/net/web/http/api/v2/graph/GraphModule.java
contains the bindings for the GraphQL engine to query custom targets.

`graphql/` contains some sample queries which can be used as in the following
example queries. The first query is a standard API v2 request to create a custom
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

Serves a GraphQL "query IDE" that can be used for testing out writing queries
and seeing the responses served for those queries by `POST /api/v2.2/graphql`.
Note the `/*` in the path - to open this in your browser while running using
`run.sh`/`smoketest.sh`, go to `https://localhost:8181/api/v2.2/graphiql/`. The
trailing slash is significant.

## GraphQL API

### Quick Reference

| What you want to do                                                       | Which handler you should use                                                    |
| ------------------------------------------------------------------------- | --------------------------------------------------------------------------------|
| **Recordings in Target JVMs**                                             |                                                                                 |
| Update metadata for a recording in a target JVM                    | [`PutActiveRecordingMetadataMutator`](#PutActiveRecordingMetadataMutator) |
| **Recordings in archive**                                                 |                                                                                 |
| Update metadata for an archived recording                          | [`PutArchivedRecordingMetadataMutator`](#PutArchivedRecordingMetadataMutator)     |

### Recordings in Target JVMs
* #### `PutActiveRecordingMetadataMutator`

    ##### synopsis
    Updates metadata for a recording in a target JVM. Overwrites any existing labels for that recording. If multiple recordings match the query, the metadata for all selected recordings will be replaced with the request metadata.

    ##### request
    `doPutMetadata(metadata: { labels: []})`

    `labels` - An array consisting of key-value label objects. The label objects should follow the `{key: "myLabelKey", value: "myValue"}` format.

    ##### response
    `ActiveRecording` - The result contains an `ActiveRecording` which can be queried for fields such as `name` and `metadata`.

    `DataFetchingException` - An argument was invalid. The body is an error message.

    ##### example
    ```
    query {
    targetNodes(filter: { name: "service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi" }) {
        recordings {
            active(filter: { name: "myActiveRecording" }) {
                doPutMetadata(metadata: { labels: [{key:"app",value:"cryostat"}, {key:"template.name",value:"Profiling"},{key:"template.type",value:"TARGET"}] }) {
                    name
                    metadata {
                        labels
                    }
                }
            }
        }
    }
    }

    {
        "data": {
            "targetNodes": [
                {
                    "recordings": {
                        "active": [
                            {
                                "doPutMetadata": {
                                    "metadata": {
                                        "labels": {
                                            "app": "cryostat",
                                            "template.name": "Continuous",
                                            "template.type": "TARGET"
                                        }
                                    },
                                    "name": "myActiveRecording"
                                }
                            }
                        ]
                    }
                }
            ]
        }
    }
    ```

### Recordings in Archives
* #### `PutArchivedRecordingMetadataMutator`

    ##### synopsis
    Updates metadata labels for a recording in Cryostat's archives. Overwrites any existing labels for that recording. If multiple recordings match the query, the metadata for all selected recordings will be replaced with the request metadata.

    ##### request
    `doPutMetadata(metadata: { labels: []})`

    `labels` - An array consisting of key-value label objects. The label objects should follow the `{key: "myLabelKey", value: "myValue"}` format.

    ##### response
    `ActiveRecording` - The result contains an `ActiveRecording` which can be queried for fields such as `name` and `metadata`.

    `DataFetchingException` - An argument was invalid. The body is an error message.

    ##### example
    ```
    query {
    targetNodes(filter: { name: "service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi" }) {
        recordings {
            archived(filter: { name: "myArchivedRecording" }) {
                data {
                    doPutMetadata(metadata: { labels: [{key:"app",value:"cryostat"}, {key:"template.name",value:"Continuous"},{key:"template.type",value:"TARGET"}] }) {
                        name
                        metadata {
                            labels
                        }
                    }
                }
            }
        }
    }
    }

    {
        "data": {
            "targetNodes": [
                {
                    "recordings": {
                        "archived": {
                            "data": [
                                {
                                    "doPutMetadata": {
                                        "metadata": {
                                            "labels": {
                                                "app": "cryostat",
                                                "template.name": "Continuous",
                                                "template.type": "TARGET"
                                            }
                                        },
                                        "name": "myArchivedRecording"
                                    }
                                }
                            ]
                        }
                    }
                }
            ]
        }
    }
    ```
