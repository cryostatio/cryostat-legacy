# HTTP API

## V1 API

### Quick Reference

| What you want to do                                                       | Which handler you should use                                                |
| ------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| **Miscellaneous**                                                         |                                                                             |
| Get a URL you can use to access Container JFR's WebSocket command channel | [`ClientUrlGetHandler`](#ClientUrlGetHandler)                               |
| Scan for and get a list of target JVMs visible to Container JFR           | [`TargetsGetHandler`](#TargetsGetHandler)                                   |
| Get a static asset from the web client                                    | [`StaticAssetsGetHandler`](#StaticAssetsGetHandler)                         |
| Send a `GET` request to a path not supported by this API                  | [`WebClientAssetsGetHandler`](#WebClientAssetsGetHandler)                   |
| Test user authentication                                                  | [`AuthPostHandler`](#AuthPostHandler)                                       |
| Get the URL of Container JFR's Grafana dashboard                          | [`GrafanaDashboardUrlGetHandler`](#GrafanaDashboardUrlGetHandler)           |
| Get the URL of Container JFR's Grafana datasource                         | [`GrafanaDatasourceUrlGetHandler`](#GrafanaDatasourceUrlGetHandler)         |
| Check the status of Container JFR's Grafana datasource and dashboard      | [`HealthGetHandler`](#HealthGetHandler)                                     |
| **Events and event templates**                                            |                                                                             |
| Get a list of event types that can be produced by a target JVM            | [`TargetEventsGetHandler`](#TargetEventsGetHandler)                         |
| Get a list of event templates known to a target JVM                       | [`TargetTemplatesGetHandler`](#TargetTemplatesGetHandler)                   |
| Download a template from a target JVM                                     | [`TargetTemplateGetHandler`](#TargetTemplateGetHandler)                     |
| Upload an event template to Container JFR                                 | [`TemplatesPostHandler`](#TemplatesPostHandler)                             |
| Delete an event template that was uploaded to Container JFR               | [`TemplateDeleteHandler`](#TemplateDeleteHandler)                           |
| **Recordings in target JVMs**                                             |                                                                             |
| Get a list of recordings in a target JVM                                  | [`TargetRecordingsGetHandler`](#TargetRecordingsGetHandler)                 |
| Get the default recording options of a target JVM                         | [`TargetRecordingOptionsGetHandler`](#TargetRecordingOptionsGetHandler)     |
| Set the default recording options of a target JVM                         | [`TargetRecordingOptionsPatchHandler`](#TargetRecordingOptionsPatchHandler) |
| Create a snapshot recording in a target JVM                               | [`TargetSnapshotPostHandler`](#TargetSnapshotPostHandler)                   |
| Start a recording in a target JVM                                         | [`TargetRecordingsPostHandler`](#TargetRecordingsPostHandler)               |
| Stop a recording in a target JVM                                          | [`TargetRecordingPatchHandler`](#TargetRecordingPatchHandler)               |
| Delete a recording in a target JVM                                        | [`TargetRecordingDeleteHandler`](#TargetRecordingDeleteHandler)             |
| Download a recording in a target JVM                                      | [`TargetRecordingGetHandler`](#TargetRecordingGetHandler)                   |
| Download a report of a recording in a target JVM                          | [`TargetReportGetHandler`](#TargetReportGetHandler)                         |
| Save a recording in a target JVM to archive                               | [`TargetRecordingPatchHandler`](#TargetRecordingPatchHandler)               |
| Upload a recording from a target JVM to the Grafana datasource            | [`TargetRecordingUploadPostHandler`](#TargetRecordingUploadPostHandler)     |
| **Recordings in archive**                                                 |                                                                             |
| Get a list of recordings in archive                                       | [`RecordingsGetHandler`](#RecordingsGetHandler)                             |
| Upload a recording to archive                                             | [`RecordingsPostHandler`](#RecordingsPostHandler)                           |
| Delete a recording from archive                                           | [`RecordingDeleteHandler`](#RecordingDeleteHandler)                         |
| Download a recording in archive                                           | [`RecordingGetHandler`](#RecordingGetHandler)                               |
| Download a report of a recording in archive                               | [`ReportGetHandler`](#ReportGetHandler)                                     |
| Upload a recording from archive to the Grafana datasource                 | [`RecordingUploadPostHandler`](#RecordingUploadPostHandler)                 |


### Core

* #### `AuthPostHandler`

    ###### synopsis
    Attempts user authentication;
    used as a simple way to check a user authentication header.

    ###### request
    `POST /api/v1/auth`

    The request should include an `Authorization` header to be checked.
    The format of this header depends on the auth manager used.
    Token-based auth managers expect `Authorization: Bearer TOKEN`,
    while basic credentials-based auth managers expect
    `Authorization: Basic ${base64(user:pass)}`.
    The `NoopAuthManager` accepts all authentication requests,
    regardless of the existence or contents of any
    `Authorization` header.
    For more details, see
    [`README.md`](https://github.com/rh-jmc-team/container-jfr#user-authentication--authorization)).

    ###### response
    `200` - No body. Getting this response means that the header is valid
    and that the user has been successfully authenticated.

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.
    Getting this response means that the header has an invalid format
    or the user has not been successfully authenticated.

    `500` - There was an unexpected error. The body is an error message.

    ###### example
    ```
    $ curl -X POST --header "Authorization: Basic dXNlcjpwYXNzCg==" localhost:8181/api/v1/auth
    ```


* #### `ClientUrlGetHandler`

    ###### synopsis
    Returns a URL that a client can connect to, to access Container JFR's
    WebSocket command channel (see [COMMANDS.md](COMMANDS.md)).

    ###### request
    `GET /api/v1/clienturl`

    ###### response
    `200` - The body is `{"clientUrl":"$URL"}`.

    `500` - The URL could not be constructed due to an error with the socket
    or the host. Or there was an unexpected error. The body is an error message.

    ###### example
    ```
    $ curl localhost:8181/api/v1/clienturl
    {"clientUrl":"ws://0.0.0.0:8181/api/v1/command"}
    ```


* #### `GrafanaDashboardUrlGetHandler`

    ###### synopsis
    Returns the URL of the Grafana dashboard that Container JFR
    is configured with (determined by the environment variable
    `GRAFANA_DASHBOARD_URL`).

    ###### request
    `GET /api/v1/grafana_dashboard_url`

    ###### response
    `200` - The body is `{"grafanaDashboardUrl":"$URL"}`.

    `500` - Container JFR is not configured with a Grafana dashboard.
    Or there was an unexpected error. The body is an error message.

    ###### example
    ```
    $ curl localhost:8181/api/v1/grafana_dashboard_url
    {"grafanaDashboardUrl":"http://localhost:3000/d/CazTnbSMz/"}
    ```


* #### `GrafanaDatasourceUrlGetHandler`

    ###### synopsis
    Returns the URL of the Grafana datasource that Container JFR
    is configured with (determined by the environment variable
    `GRAFANA_DATASOURCE_URL`).

    ###### request
    `GET /api/v1/grafana_datasource_url`

    ###### response
    `200` - The body is `{"grafanaDatasourceUrl":"$URL"}`

    `500` - Container JFR is not configured with a Grafana datasource.
    Or there was an unexpected error. The body is an error message.

    ###### example
    ```
    $ curl localhost:8181/api/v1/grafana_datasource_url
    {"grafanaDatasourceUrl":"http://localhost:8080"}
    ```


* #### `HealthGetHandler`

    ###### synopsis
    Returns whether or not the Grafana datasource and Grafana dashboard
    that Container JFR is configured with are running properly.
    Can also be used to see if Container JFR itself is running properly,
    by checking for a valid response.

    ###### request
    `GET /health`

    ###### response
    `200` - The body is
    `{"datasourceAvailable":$DATASOURCE_AVAILABLE,"dashboardAvailable":$DASHBOARD_AVAILABLE}`.

    `$DATASOURCE_AVAILABLE` is `true` if  Container JFR is configured with a
    Grafana datasource and that datasource responds to a `GET` request
    with a `200`, and it is `false` otherwise.

    `$DASHBOARD_AVAILABLE` is `true` if  Container JFR is configured with a
    Grafana dashboard and that dashboard responds to a `GET` request
    with a `200`, and it is `false` otherwise.

    `500` - There was an unexpected error. The body is an error message.

    ###### example
    ```
    $ curl localhost:8181/health
    {"dashboardAvailable":false,"datasourceAvailable":false}
    ```


* #### `StaticAssetsGetHandler`

    ###### synopsis
    Returns a static asset of the Container JFR web client.

    ###### request
    `GET /*`

    The path should be the directory path of the desired asset,
    assuming a root of
    `target/assets/app/resources/com/redhat/rhjmc/containerjfr/net/web/`.

    ###### response

    `200` - The body is the requested asset. Note that if the requested asset
    does not exist or the path is invalid, the request will not be handled here
    and instead  will be routed to
    [`WebClientAssetsGetHandler`](#WebClientAssetsGetHandler).

    `500` - There was an unexpected error. The body is an error message.

    ###### example
    ```
    $ curl localhost:8181/images/pfbg_992@2x.jpg --output pfbg_992@2x.jpg
      % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                     Dload  Upload   Total   Spent    Left  Speed
    100  574k  100  574k    0     0  43.1M      0 --:--:-- --:--:-- --:--:-- 43.1M
    ```


* #### `TargetsGetHandler`

    ###### synopsis
    Scans for and returns discoverable target JVMs.

    ###### request
    `GET /api/v1/targets`

    ###### response
    `200` - The body is a JSON array of target objects.

    The format for a target is
    `{"connectUrl":"$CONNECT_URL","alias":"$ALIAS"}`.

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `427` - JMX authentication failed. The body is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `500` - There was an unexpected error. The body is an error message.

    ###### example
    ```
    $ curl localhost:8181/api/v1/targets
    [{"connectUrl":"service:jmx:rmi:///jndi/rmi://container-jfr:9091/jmxrmi","alias":"com.redhat.rhjmc.containerjfr.ContainerJfr"}]
    ```


* #### `WebClientAssetsGetHandler`

    ###### synopsis
    Handles all `GET` requests to paths not covered by another handler;
    returns a simple `index.html`.

    ###### request
    `GET /*`

    ###### response
    `200` - The body is the Container JFR web client's
    `index.html` HTML document.

    `500` - There was an unexpected error. The body is an error message.

    ###### example
    ```
    $ curl localhost:8181/foo
    <!doctype html><html lang="en-US"><head><meta charset="utf-8"><title>ContainerJFR</title><meta id="appName" name="application-name" content="ContainerJFR"><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="icon" type="image/png" href="https://www.patternfly.org/components/patternfly/dist/img/favicon.ico"><base href="/"><link href="app.css" rel="stylesheet"></head><body><noscript>Enabling JavaScript is required to run this app.</noscript><div id="root"></div><script src="app.bundle.js"></script></body></html>
    ```


### Flight Recorder

* #### `RecordingDeleteHandler`

    ###### synopsis
    Deletes a recording that was saved to archive.
    This does not affect any recordings in any target JVM's JFR buffer.

    ###### request
    `DELETE /api/v1/recordings/:recordingName`

    `recordingName` - The name of the saved recording to delete.
    Should use percent-encoding.

    ###### response
    `200` - No body.

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - The recording could not be found. The body is an error message.

    `427` - JMX authentication failed. The body is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `500` - The recording was found but it could not be deleted.
    Or there was an unexpected error. The body is an error message.

    ###### example
    ```
    $ curl -X DELETE localhost:8181/api/v1/recordings/localhost_foo_20200910T213341Z.jfr
    ```


* #### `RecordingGetHandler`

    ###### synopsis
    Returns a recording that was saved to archive,
    as an octet stream.

    ###### request
    `GET /api/v1/recordings/:recordingName`

    `recordingName` - The name of the saved recording to get.
    Should use percent-encoding.

    ###### response
    `200` - The body is an octet stream consisting of the requested recording.

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - The recording could not be found. The body is an error message.

    `427` - JMX authentication failed. The body is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `500` - The recording was found but it could not be written to the response.
    Or there was an unexpected error. The body is an error message.

    ###### example
    ```
    $ curl localhost:8181/api/v1/recordings/localhost_foo_20200910T214559Z.jfr --output foo.jfr
      % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                     Dload  Upload   Total   Spent    Left  Speed
    100  942k  100  942k    0     0  61.3M      0 --:--:-- --:--:-- --:--:-- 61.3M
    ```


* #### `RecordingsGetHandler`

    ###### synopsis
    Returns a list of the recordings that are saved in archive.

    ###### request
    `GET /api/v1/recordings`

    ###### response
    `200` - The body is a JSON array of recording objects.

    The format for a recording is
    `{"downloadUrl":"$DOWNLOAD_URL","name":"$NAME","reportUrl":"$REPORT_URL"}`.

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `427` - JMX authentication failed. The body is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `500` - There was an unexpected error. The body is an error message.

    `501` - The archive path where recordings are saved could not be accessed.
    The body is an error message.

    ###### example
    ```
    $ curl localhost:8181/api/v1/recordings
    [{"downloadUrl":"http://192.168.0.109:8181/api/v1/recordings/localhost_foo_20200903T202547Z.jfr","name":"localhost_foo_20200903T202547Z.jfr","reportUrl":"http://192.168.0.109:8181/api/v1/reports/localhost_foo_20200903T202547Z.jfr"}]
    ```


* #### `RecordingsPostHandler`

    ###### synopsis
    Uploads a recording from the client to Container JFR's archive.

    ###### request
    `POST /api/v1/recordings`

    The recording should be uploaded in a form with the name `recording`.
    The filename of the recording must be in the following format, which is
    the same format that Container JFR uses for recordings it saves itself
    (all fields in brackets are optional):
    ```
    [$TARGET_NAME]_[$RECORDING_NAME]_[$DATE]T[$TIME]Z[.$COUNTER][.jfr]
    ```

    `TARGET_NAME` - The name of the target JVM (alphanumerics and hyphens allowed)

    `RECORDING_NAME` - The name of the recording
    (alphanumerics, hyphens, and underscores allowed)

    `DATE` - The date of the recording (numbers allowed)

    `TIME` - The time of the recording (numbers allowed)

    `COUNTER` - An additional number used to avoid name collisions
    (numbers allowed)

    Formally, the required format is:
    ```
    ([A-Za-z\d-]*)_([A-Za-z\d-_]*)_([\d]*T[\d]*Z)(\.[\d]+)?(\.jfr)?
    ```

    ###### response
    `200` - The body is `{"name":"$NAME"}`, where `$NAME` is the name of the
    recording that is now saved in archive.
    This name may be different from the filename of the uploaded file
    for two reasons.

    First, if there is a name collision, a counter will be added to the
    original filename, or the existing counter will be modified,
    and the new counter will be set to the next available number,
    starting from `2`. A counter of `1` will be removed if there is no
    name conflict, and modified to the next available number if there is.

    And second, if the filename of the uploaded file does not include a `.jfr`
    ending, one will be added.

    `400` - The recording submission is invalid. The body is an error
    message.

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `427` - JMX authentication failed. The body is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `500` - There was an unexpected error. The body is an error message.

    `503` - `CONTAINER_JFR_ARCHIVE_PATH` is an invalid directory.
    The body is an error message.

    ###### example
    ```
    $ curl --form "recording=@localhost_foo_20200903T202547Z.jfr" localhost:8181/api/v1/recordings
    {"name":"localhost_foo_20200903T202547Z.2.jfr"}
    ```


* #### `RecordingUploadPostHandler`

    ###### synopsis
    Uploads a recording that was saved to archive to
    the Grafana datasource that Container JFR is configured with
    (determined by the environment variable `GRAFANA_DATASOURCE_URL`).

    ###### request
    `POST /api/v1/recordings/:recordingName/upload`

    `recordingName` - The name of the saved recording to upload.
    Should use percent-encoding.

    ###### response
    `200` - The body is the body of the response that Container JFR got
    after sending the upload request to the Grafana datasource server.

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - The recording could not be found. The body is an error message.

    `427` - JMX authentication failed. The body is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `500` - There was an unexpected error. The body is an error message.

    `501` - The Grafana datasource URL is malformed.
    The body is an error message.

    `502` - Container JFR received an invalid response from the
    Grafana datasource server after sending the upload request.
    The body is an error message.

    ###### example
    ```
    $ curl -X POST localhost:8181/api/v1/recordings/localhost_foo_20200911T144545Z.jfr/upload
    Uploaded: file-uploads/555f4dab-240b-486b-b336-2d0e5f43e7cd
    Loaded: file-uploads/555f4dab-240b-486b-b336-2d0e5f43e7cd
    ```


* #### `ReportGetHandler`

    ###### synopsis
    Returns the report of a recording that was saved to archive.

    ###### request
    `GET /api/v1/reports/:recordingName`

    `recordingName` - The name of the recording to get the report for.
    Should use percent-encoding.

    ###### response
    `200` - The body is the requested report, as an HTML document.

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - The report could not be found. The body is an error message.

    `427` - JMX authentication failed. The body is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `500` - There was an unexpected error. The body is an error message.

    ###### example
    ```
    $ curl localhost:8181/api/v1/reports/localhost_foo_20200911T144545Z.jfr --output report.html
      % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                     Dload  Upload   Total   Spent    Left  Speed
    100  116k  100  116k    0     0   134k      0 --:--:-- --:--:-- --:--:--  134k
    ```


* #### `TargetEventsGetHandler`

    ###### synopsis
    Returns a list of event types that can be produced by a target JVM.

    ###### request
    `GET /api/v1/targets/:targetId/events`

    `targetId` - The location of the target JVM to connect to,
    in the form of a `service:rmi:jmx://` JMX Service URL, or `hostname:port`.
    Should use percent-encoding.

    ###### response
    `200` - The body is a JSON array of event type objects.

    The format for an event type is
    `{"name":"$NAME","typeId":"$TYPE_ID","description":"$DESCRIPTION",
    "category":[$CATEGORIES],"options":{$OPTIONS}}`

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - The target could not be found. The body is an error message.

    `427` - JMX authentication failed. The body is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `500` - There was an unexpected error. The body is an error message.

    ###### example
    ```
    $ curl localhost:8181/api/v1/targets/localhost/events --output events
      % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                     Dload  Upload   Total   Spent    Left  Speed
    100 47013  100 47013    0     0   866k      0 --:--:-- --:--:-- --:--:--  866k
    ```


* #### `TargetRecordingDeleteHandler`

    ###### synopsis
    Deletes a recording from a target JVM, removing it from the target JVM
    and freeing the buffer memory used. The recording will be stopped if
    it is running at the time of deletion.

    ###### request
    `DELETE /api/v1/targets/:targetId/recordings/:recordingName`

    `targetId` - The location of the target JVM to connect to,
    in the form of a `service:rmi:jmx://` JMX Service URL, or `hostname:port`.
    Should use percent-encoding.

    `recordingName` - The name of the recording to delete.
    Should use percent-encoding.

    ###### response
    `200` - No body.

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - The target or the recording could not be found.
    The body is an error message.

    `427` - JMX authentication failed. The body is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `500` - There was an unexpected error. The body is an error message.

    ###### example
    ```
    $ curl -X DELETE localhost:8181/api/v1/targets/localhost/recordings/foo
    ```


* #### `TargetRecordingGetHandler`

    ###### synopsis
    Returns a recording of a target JVM, as an octet stream.

    ###### request
    `GET /api/v1/targets/:targetId/recordings/:recordingName`

    `targetId` - The location of the target JVM to connect to,
    in the form of a `service:rmi:jmx://` JMX Service URL, or `hostname:port`.
    Should use percent-encoding.

    `recordingName` - The name of the recording to get.
    Should use percent-encoding.

    ###### response
    `200` - The body is an octet stream consisting of the requested recording.

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - The target or the recording could not be found.
    The body is an error message.

    `427` - JMX authentication failed. The body is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `500` - The recording was found but it could not be written to the
    response. Or there was an unexpected error. The body is an error message.

    ###### example
    ```
    $ curl localhost:8181/api/v1/targets/localhost/recordings/foo --output foo.jfr
      % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                     Dload  Upload   Total   Spent    Left  Speed
    100  530k    0  530k    0     0  9303k      0 --:--:-- --:--:-- --:--:-- 9303k
    ```

* ### `TargetRecordingOptionsGetHandler`

    ###### synopsis
    Returns the default recording options of a target JVM.

    ###### request
    `GET /api/v1/targets/:targetId/recordingOptions`.

    `targetId` - The location of the target JVM to connect to,
    in the form of a `service:rmi:jmx://` JMX Service URL, or `hostname:port`.
    Should use percent-encoding.

    ###### response
    `200` - The body is `{"maxAge":"$MAX_AGE","toDisk":"TO_DISK","maxSize":"MAX_SIZE"}`.

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - The target could not be found.  The body is an error message.

    `427` - JMX authentication failed. The body is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `500` - There was an unexpected error. The body is an error message.

    ###### example
    ```
    $ curl localhost:8181/api/v1/targets/localhost/recordingOptions
    {"maxAge":0,"toDisk":false,"maxSize":0}
    ```


* ### `TargetRecordingOptionsPatchHandler`

    ###### synopsis
    Sets the default recording options of a target JVM.

    ###### request
    `PATCH /api/v1/targets/:targetId/recordingOptions`.

    `targetId` - The location of the target JVM to connect to,
    in the form of a `service:rmi:jmx://` JMX Service URL, or `hostname:port`.
    Should use percent-encoding.

    **The request must include the following fields:**

    `toDisk` - Whether a recording is stored to disk;
    either `true` or `false`.

    **The request must include the following fields:**

    `maxAge` - The maximum event age of a recording, in seconds.
    A value of zero means there is no maximum event age.

    `maxSize` - The maximum size of a recording, in bytes.
    A value of zero means there is no maximum recording size.

    ###### response
    `200` - The body is the updated default recording options of the
    target JVM, in the form
    `{"maxAge":"$MAX_AGE","toDisk":"TO_DISK","maxSize":"MAX_SIZE"}`.

    `400` - An argument was invalid. The body is an error message.

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - The target could not be found. The body is an error message.

    `427` - JMX authentication failed. The body is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `500` - There was an unexpected error. The body is an error message.

    ###### example
    ```
    $ curl -X PATCH --data "toDisk=true&maxAge=0" localhost:8181/api/v1/targets/localhost/recordingOptions
    {"maxAge":0,"toDisk":true,"maxSize":0}
    ```


* #### `TargetRecordingPatchHandler`

    ###### synopsis
    **STOP**

    Stops a recording in a target JVM.

    **SAVE**

    Saves a recording in a target JVM to archive.
    The default directory used is `/flghtrecordings`, but the environment
    variable `CONTAINER_JFR_ARCHIVE_PATH` can be used to specify a different
    path.

    ###### request
    `PATCH /api/v1/targets/:targetId/recordings/:recordingName`

    `targetId` - The location of the target JVM to connect to,
    in the form of a `service:rmi:jmx://` JMX Service URL, or `hostname:port`.
    Should use percent-encoding.

    `recordingName` - The name of the recording to patch.
    Should use percent-encoding.

    The body must be either `STOP`, to stop the recording,
    or `SAVE`, to save the recording (case insensitive).

    ###### response
    **General**

    `400` - The operation is unsupported. The body is an error message.

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - The target or the recording could not be found.
    The body is an error message.

    `427` - JMX authentication failed. The body is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `500` - There was an unexpected error. The body is an error message.

    **STOP**

    `200` - No body.

    `500` - The recording could not be stopped. The body is an error message.

    **SAVE**

    `200` - The body is the name of the recording that was saved.
    Note that this name will be different from the recording's original name,
    to add metadata.

    ###### example
    ```
    $ curl -X PATCH --data "STOP" localhost:8181/api/v1/targets/localhost/recordings/foo

    $ curl -X PATCH --data "SAVE" http://localhost:8181/api/v1/targets/localhost/recordings/foo
    localhost_foo_20200911T155146Z.jfr
    ```


* #### `TargetRecordingsGetHandler`

    ###### synopsis
    Returns a list of all the recordings in a target JVM.

    ###### request
    `GET /api/v1/targets/:targetId/recordings`

    `targetId` - The location of the target JVM to connect to,
    in the form of a `service:rmi:jmx://` JMX Service URL, or `hostname:port`.
    Should use percent-encoding.

    ###### response
    `200` - The body is a JSON array of recording objects.

    The format for a recording is
    `{"downloadUrl":"$DOWNLOAD_URL","reportUrl":"$REPORT_URL","id":$ID,"name":"$NAME","state":"$STATE","startTime":$START_TIME,"duration":$DURATION,"continuous":$CONTINUOUS,"toDisk":$TO_DISK,"maxSize":$MAX_SIZE,"maxAge":$MAX_AGE}`.

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - The target could not be found. The body is an error message.

    `427` - JMX authentication failed. The body is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `500` - There was an unexpected error. The body is an error message.

    ###### example
    ```
    $ curl localhost:8181/api/v1/targets/localhost/recordings
    [{"downloadUrl":"http://0.0.0.0:8181/api/v1/targets/service:jmx:rmi:%2F%2F%2Fjndi%2Frmi:%2F%2Flocalhost:9091%2Fjmxrmi/recordings/foo","reportUrl":"http://0.0.0.0:8181/api/v1/targets/service:jmx:rmi:%2F%2F%2Fjndi%2Frmi:%2F%2Flocalhost:9091%2Fjmxrmi/reports/foo","id":3,"name":"foo","state":"STOPPED","startTime":1599839450919,"duration":5000,"continuous":false,"toDisk":true,"maxSize":0,"maxAge":0}]
    ```


* #### `TargetRecordingsPostHandler`

    ###### synopsis
    Starts a recording in a target JVM.

    ###### request
    `POST /api/v1/targets/:targetId/recordings`

    `targetId` - The location of the target JVM to connect to,
    in the form of a `service:rmi:jmx://` JMX Service URL, or `hostname:port`.
    Should use percent-encoding.

    **The request must include the following fields:**

    `recordingName` - The name of the recording to create.
    Should use percent-encoding.

    `events` - The events configuration for the recording.
    This can be a comma-seperated list of events, with each event having the
    form `$EVENT_ID:$OPTION=$VALUE`; or it can be a template, using the form
    `template=$TEMPLATE`.

    **The request may include the following fields:**

    `duration` - The duration of the recording, in seconds.
    If this field is not set, or if it is set to zero,
    the recording will be continuous,
    meaning it will run until it is manually stopped, for example with
    [`TargetRecordingPatchHandler`](#TargetRecordingPatchHandler).

    `toDisk` - Whether the recording is stored to disk;
    either `true` or `false`. If this field is not set,
    it will default to `true`.

    `maxAge` - The maximum event age of the recording, in seconds.
    If this field is not set, or if it is set to zero,
    the recording will not have a maximum event age.

    `maxSize` - The maximum size of the recording, in bytes.
    If this field is not set, or if it is set to zero,
    the recording will not have a maximum size.

    ###### response
    `201` - The body is a descriptor of the newly started recording, in the form
    `{"downloadUrl":"$DOWNLOAD_URL","reportUrl":"$REPORT_URL","id":$ID,"name":"$NAME","state":"$STATE","startTime":$START_TIME,"duration":$DURATION,"continuous":$CONTINUOUS,"toDisk":$TO_DISK,"maxSize":$MAX_SIZE,"maxAge":$MAX_AGE}`.

    `400` - An argument was invalid. The body is an error message.

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - The target could not be found. The body is an error message.

    `427` - JMX authentication failed. The body is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `500` - There was an unexpected error. The body is an error message.

    ###### example
    ```
    $ curl --data "recordingName=foo&duration=5&events=template=ALL" localhost:8181/api/v1/targets/localhost/recordings
    {"downloadUrl":"http://0.0.0.0:8181/api/v1/targets/service:jmx:rmi:%2F%2F%2Fjndi%2Frmi:%2F%2Flocalhost:9091%2Fjmxrmi/recordings/foo","reportUrl":"http://0.0.0.0:8181/api/v1/targets/service:jmx:rmi:%2F%2F%2Fjndi%2Frmi:%2F%2Flocalhost:9091%2Fjmxrmi/reports/foo","id":1,"name":"foo","state":"RUNNING","startTime":1599847667483,"duration":5000,"continuous":false,"toDisk":true,"maxSize":0,"maxAge":0}
    ```


* #### `TargetRecordingUploadPostHandler`

    ###### synopsis
    Uploads a recording of a target JVM to the Grafana datasource
    that Container JFR is configured with
    (determined by the environment variable `GRAFANA_DATASOURCE_URL`).

    ###### request
    `POST /api/v1/targets/:targetId/recordings/:recordingName/upload`

    `targetId` - The location of the target JVM to connect to,
    in the form of a `service:rmi:jmx://` JMX Service URL, or `hostname:port`.
    Should use percent-encoding.

    `recordingName` - The name of the recording to upload.
    Should use percent-encoding.

    ###### response
    `200` - The body is the body from the response that Container JFR got
    after sending the upload request to the Grafana datasource.

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - The target or the recording could not be found.
    The body is an error message.

    `427` - JMX authentication failed. The body is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `500` - There was an unexpected error. The body is an error message.

    `501` - The Grafana datasource URL is malformed.
    The body is an error message.

    `502` - Container JFR received an invalid response from the
    Grafana datasource after sending the upload request.
    The body is an error message.

    ###### example
    ```
    $ curl -X POST localhost:8181/api/v1/targets/localhost/recordings/foo/upload
    Uploaded: file-uploads/72ee43b8-c858-4aef-ae7e-4bcf7d93ec7c
    Loaded: file-uploads/72ee43b8-c858-4aef-ae7e-4bcf7d93ec7c
    ```


* #### `TargetReportGetHandler`

    ###### synopsis
    Returns a report of a recording of a target JVM.

    ###### request
    `GET /api/v1/targets/:targetId/reports/:recordingName`

    `targetId` - The location of the target JVM to connect to,
    in the form of a `service:rmi:jmx://` JMX Service URL, or `hostname:port`.
    Should use percent-encoding.

    `recordingName` - The name of the recording to get the report for.
    Should use percent-encoding.

    ###### response
    `200` - The body is the requested report, as an HTML document.

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - The report could not be found, or the target could not be found.
    The body is an error message.

    `427` - JMX authentication failed. The body is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `500` - There was an unexpected error. The body is an error message.

    ###### example
    ```
    $ curl localhost:8181/api/v1/targets/localhost/reports/foo --output report.html
      % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                     Dload  Upload   Total   Spent    Left  Speed
    100  118k  100  118k    0     0   109k      0  0:00:01  0:00:01 --:--:--  109k
    ```


* #### `TargetSnapshotPostHandler`

    ###### synopsis
    Creates a recording named `snapshot-n`, where `n` is a sequentially
    assigned ID, which contains information about all events recorded
    across all active recordings at the time of invocation.

    ###### request
    `POST /api/v1/targets/:targetId/snapshot`

    `targetId` - The location of the target JVM to connect to,
    in the form of a `service:rmi:jmx://` JMX Service URL, or `hostname:port`.
    Should use percent-encoding.

    ###### response
    `200` - The body is the name of the recording.

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - The target could not be found. The body is an error message.

    `427` - JMX authentication failed. The body is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `500` - There was an unexpected error. The body is an error message.

    ###### example
    ```
    $ curl -X POST localhost:8181/api/v1/targets/localhost/snapshot
    snapshot-2
    ```


* #### `TargetTemplateGetHandler`

    ###### synopsis
    Returns an event template from a target JVM.

    ###### request
    `GET /api/v1/targets/:targetId/templates/:templateName/type/:templateType`

    `targetId` - The location of the target JVM to connect to,
    in the form of a `service:rmi:jmx://` JMX Service URL, or `hostname:port`.
    Should use percent-encoding.

    `templateName` - The name of the template to get.

    `templateType` - The type of the template to get.

    ###### response
    `200` - The body is the requested event template, as an XML document.

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - The target or the template or the template type could not be found.
    The body is an error message.

    `427` - JMX authentication failed. The body is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `500` - There was an unexpected error. The body is an error message.

    ###### example
    ```
    $ curl localhost:8181/api/v1/targets/localhost/templates/Profiling/type/TARGET --output Continuous.jfc
      % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                     Dload  Upload   Total   Spent    Left  Speed
    100 30133  100 30133    0     0   239k      0 --:--:-- --:--:-- --:--:--  239k
    ```


* #### `TargetTemplatesGetHandler`

    ###### synopsis
    Returns a list of event templates known to a target JVM.

    ###### request
    `GET /api/v1/targets/:targetId/templates`

    `targetId` - The location of the target JVM to connect to,
    in the form of a `service:rmi:jmx://` JMX Service URL, or `hostname:port`.
    Should use percent-encoding.

    ###### response
    `200` - The body is a JSON array of template objects.

    The format for a template is
    `{"name":"$NAME","description":"$DESCRIPTION","provider":"$PROVIDER","type":"$TYPE"}`.

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - The target could not be found. The body is an error message.

    `427` - JMX authentication failed. The body is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `500` - There was an unexpected error. The body is an error message.

    ###### example
    ```
    $ curl localhost:8181/api/v1/targets/localhost/templates
    [{"name":"Profiling","description":"Low overhead configuration for profiling, typically around 2 % overhead.","provider":"Oracle","type":"TARGET"},{"name":"Continuous","description":"Low overhead configuration safe for continuous use in production environments, typically less than 1 % overhead.","provider":"Oracle","type":"TARGET"},{"name":"ALL","description":"Enable all available events in the target JVM, with default option values. This will be very expensive and is intended primarily for testing ContainerJFR's own capabilities.","provider":"ContainerJFR","type":"TARGET"}]
    ```


* #### `TemplateDeleteHandler`

    ###### synopsis
    Deletes an event template that was uploaded to Container JFR and is stored
    in `CONTAINER_JFR_TEMPLATE_PATH`.

    ###### request
    `DELETE /api/v1/templates/:templateName`

    `templateName` - The name of the template to delete.
    Note that this should be the value of the `label` element of the
    event template, not the filename of the uploaded file itself.

    ###### response
    `200` - No body.

    `400` - The template could not be found. The body is an error message.

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `427` - JMX authentication failed. The body is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `500` - `CONTAINER_JFR_TEMPLATE_PATH` is not set,
    or it is set to an invalid path, or there was an unexpected error.
    The body is an error message.

    ###### example
    ```
    $ curl -X DELETE localhost:8181/api/v1/templates/Foo
    ```


* #### `TemplatesPostHandler`

    ###### synopsis
    Uploads an event template and makes it available for use by Container JFR.
    The template is saved to the path specified by
    `CONTAINER_JFR_TEMPLATE_PATH`.

    ###### request
    `POST /api/v1/templates`

    The template should be uploaded in a form with the name `template`.

    ###### response
    `200` - No body.

    `400` - The template being uploaded is an invalid event template.
    The body is an error message.

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `427` - JMX authentication failed. The body is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `500` - `CONTAINER_JFR_TEMPLATE_PATH` is not set,
    or it is set to an invalid path, or there was an unexpected error.
    The body is an error message.

    ###### example
    ```
    $ curl --form "template=@Foo.jfc" localhost:8181/api/v1/templates
    ```

## V2 API

### Quick Reference

| What you want to do                                                       | Which handler you should use                                                |
| ------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| **Recordings in target JVMs**                                             |                                                                             |
| Create a snapshot recording in a target JVM                               | [`TargetSnapshotPostHandler`](#TargetSnapshotPostHandler)                   |
| **Security**                                                              |                                                                             |
| Upload an SSL Certificate                                                 | [`CertificatePostHandler`](#CertificatePostHandler)                         |

### Flight Recorder

* #### `TargetSnapshotPostHandler`

    ###### synopsis
    Creates a recording named `snapshot-n`, where `n` is a sequentially
    assigned ID, which contains information about all events recorded
    across all active recordings at the time of invocation.

    ###### request
    `POST /api/v2/targets/:targetId/snapshot`

    `targetId` - The location of the target JVM to connect to,
    in the form of a `service:rmi:jmx://` JMX Service URL, or `hostname:port`.
    Should use percent-encoding.

    ###### response
    `201` - The body is a descriptor of the newly started recording, in the form
    `{"downloadUrl":"$DOWNLOAD_URL","reportUrl":"$REPORT_URL","id":$ID,"name":"$NAME","state":"$STATE","startTime":$START_TIME,"duration":$DURATION,"continuous":$CONTINUOUS,"toDisk":$TO_DISK,"maxSize":$MAX_SIZE,"maxAge":$MAX_AGE}`. The `Location` header will also be set
    to the same URL as in the `downloadUrl` field.

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - The target could not be found. The body is an error message.

    `427` - JMX authentication failed. The body is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `500` - There was an unexpected error. The body is an error message.

    ###### example
    ```
    $ curl -X POST localhost:8181/api/v2/targets/localhost/snapshot
    {"downloadUrl":"http://192.168.0.109:8181/api/v1/targets/service:jmx:rmi:%2F%2F%2Fjndi%2Frmi:%2F%2Flocalhost:9091%2Fjmxrmi/recordings/snapshot-1","reportUrl":"http://192.168.0.109:8181/api/v1/targets/service:jmx:rmi:%2F%2F%2Fjndi%2Frmi:%2F%2Flocalhost:9091%2Fjmxrmi/reports/snapshot-1","id":1,"name":"snapshot-1","state":"STOPPED","startTime":1601998841300,"duration":0,"continuous":true,"toDisk":true,"maxSize":0,"maxAge":0}
    ```

### Security

* #### `CertificatePostHandler`

    ###### synopsis
    Uploads an SSL Certificate from the client, and saves it to the truststore directory.

    ###### request
    `POST /api/v2/certificates`

    The certificate must be DER-encoded and can be either binary of base64. The supported extensions are .der, .cer, .pem. 
    The certificate should be uploaded in a form with the name `cert`.

    ###### response
    `200` - The body is `saved: ` followed by the path of the saved file.

    `400` - No `cert` was found in the request form. The body is the error message `A file named "cert" was not included in the request`.
    
    `409` - A certificate with the same filename already exists in the truststore directory. The body includes the path where the file already exists.

    `500` - The `TRUSTSTORE_DIR` environment variable is not set, or there is an unexpected error. The body is an error message.

    ###### example
    ```
    $ curl -F cert=@vertx-fib-demo.cer https://localhost:8181/api/v2/certificates
    ```
