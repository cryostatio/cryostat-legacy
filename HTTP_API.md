# HTTP API

* [V1](#V1-API)
* [V2](#V2-API)

## V1 API

### Quick Reference

| What you want to do                                                       | Which handler you should use                                                |
| ------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| **Miscellaneous**                                                         |                                                                             |
| Get a URL you can use to access Cryostat's WebSocket notification channel | [`NotificationsUrlGetHandler`](#NotificationsUrlGetHandler)                               |
| Scan for and get a list of target JVMs visible to Cryostat                | [`TargetsGetHandler`](#TargetsGetHandler)                                   |
| Get a static asset from the web client                                    | [`StaticAssetsGetHandler`](#StaticAssetsGetHandler)                         |
| Send a `GET` request to a path not supported by this API                  | [`WebClientAssetsGetHandler`](#WebClientAssetsGetHandler)                   |
| Test user authentication                                                  | [`AuthPostHandler`](#AuthPostHandler)                                       |
| Get the URL of Cryostat's Grafana dashboard                               | [`GrafanaDashboardUrlGetHandler`](#GrafanaDashboardUrlGetHandler)           |
| Get the URL of Cryostat's Grafana datasource                              | [`GrafanaDatasourceUrlGetHandler`](#GrafanaDatasourceUrlGetHandler)         |
| Check the status of Cryostat's Grafana datasource and dashboard           | [`HealthGetHandler`](#HealthGetHandler)                                     |
| **Events and event templates**                                            |                                                                             |
| Get a list of event types that can be produced by a target JVM            | [`TargetEventsGetHandler`](#TargetEventsGetHandler)                         |
| Get a list of event templates known to a target JVM                       | [`TargetTemplatesGetHandler`](#TargetTemplatesGetHandler)                   |
| Download a template from a target JVM                                     | [`TargetTemplateGetHandler`](#TargetTemplateGetHandler)                     |
| Upload an event template to Cryostat                                      | [`TemplatesPostHandler`](#TemplatesPostHandler)                             |
| Delete an event template that was uploaded to Cryostat                    | [`TemplateDeleteHandler`](#TemplateDeleteHandler)                           |
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
    [`README.md`](https://github.com/cryostatio/cryostat#user-authentication--authorization)).

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


* #### `NotificationsUrlGetHandler`

    ###### synopsis
    Returns a URL that a client can connect to, to access Cryostat's
    WebSocket notification channel.

    ###### request
    `GET /api/v1/notifications_url`

    ###### response
    `200` - The body is `{"notificationsUrl":"$URL"}`.

    `500` - The URL could not be constructed due to an error with the socket
    or the host. Or there was an unexpected error. The body is an error message.

    ###### example
    ```
    $ curl localhost:8181/api/v1/notifications_url
    {"notificationsUrl":"ws://0.0.0.0:8181/api/v1/notifications"}
    ```


* #### `GrafanaDashboardUrlGetHandler`

    ###### synopsis
    Returns the URL of the Grafana dashboard that Cryostat
    is configured with (determined by the environment variable
    `GRAFANA_DASHBOARD_URL`).

    ###### request
    `GET /api/v1/grafana_dashboard_url`

    ###### response
    `200` - The body is `{"grafanaDashboardUrl":"$URL"}`.

    `500` - Cryostat is not configured with a Grafana dashboard.
    Or there was an unexpected error. The body is an error message.

    ###### example
    ```
    $ curl localhost:8181/api/v1/grafana_dashboard_url
    {"grafanaDashboardUrl":"http://localhost:3000/d/CazTnbSMz/"}
    ```


* #### `GrafanaDatasourceUrlGetHandler`

    ###### synopsis
    Returns the URL of the Grafana datasource that Cryostat
    is configured with (determined by the environment variable
    `GRAFANA_DATASOURCE_URL`).

    ###### request
    `GET /api/v1/grafana_datasource_url`

    ###### response
    `200` - The body is `{"grafanaDatasourceUrl":"$URL"}`

    `500` - Cryostat is not configured with a Grafana datasource.
    Or there was an unexpected error. The body is an error message.

    ###### example
    ```
    $ curl localhost:8181/api/v1/grafana_datasource_url
    {"grafanaDatasourceUrl":"http://localhost:8080"}
    ```


* #### `HealthGetHandler`

    ###### synopsis
    Returns whether or not the Grafana datasource and Grafana dashboard
    that Cryostat is configured with are running properly.
    Can also be used to see if Cryostat itself is running properly,
    by checking for a valid response.

    ###### request
    `GET /health`

    ###### response
    `200` - The body is
    `{"datasourceAvailable":$DATASOURCE_AVAILABLE,"dashboardAvailable":$DASHBOARD_AVAILABLE}`.

    `$DATASOURCE_AVAILABLE` is `true` if  Cryostat is configured with a
    Grafana datasource and that datasource responds to a `GET` request
    with a `200`, and it is `false` otherwise.

    `$DASHBOARD_AVAILABLE` is `true` if  Cryostat is configured with a
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
    Returns a static asset of the Cryostat web client.

    ###### request
    `GET /*`

    The path should be the directory path of the desired asset,
    assuming a root of
    `target/assets/app/resources/io/cryostat/net/web/`.

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

    `500` - There was an unexpected error. The body is an error message.

    ###### example
    ```
    $ curl localhost:8181/api/v1/targets
    [{"connectUrl":"service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi","alias":"io.cryostat.Cryostat"}]
    ```


* #### `WebClientAssetsGetHandler`

    ###### synopsis
    Handles all `GET` requests to paths not covered by another handler;
    returns a simple `index.html`.

    ###### request
    `GET /*`

    ###### response
    `200` - The body is the Cryostat web client's `index.html` HTML document.

    `500` - There was an unexpected error. The body is an error message.

    ###### example
    ```
    $ curl localhost:8181/foo
    <!doctype html><html lang="en-US"><head><meta charset="utf-8"><title>Cryostat</title><meta id="appName" name="application-name" content="Cryostat"><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="icon" type="image/png" href="https://www.patternfly.org/components/patternfly/dist/img/favicon.ico"><base href="/"><link href="app.css" rel="stylesheet"></head><body><noscript>Enabling JavaScript is required to run this app.</noscript><div id="root"></div><script src="app.bundle.js"></script></body></html>
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
    Uploads a recording from the client to Cryostat's archive.

    ###### request
    `POST /api/v1/recordings`

    The recording should be uploaded in a form with the name `recording`.
    The filename of the recording must be in the following format, which is
    the same format that Cryostat uses for recordings it saves itself
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

    `500` - There was an unexpected error. The body is an error message.

    `503` - `CRYOSTAT_ARCHIVE_PATH` is an invalid directory.
    The body is an error message.

    ###### example
    ```
    $ curl --form "recording=@localhost_foo_20200903T202547Z.jfr" localhost:8181/api/v1/recordings
    {"name":"localhost_foo_20200903T202547Z.2.jfr"}
    ```


* #### `RecordingUploadPostHandler`

    ###### synopsis
    Uploads a recording that was saved to archive to
    the Grafana datasource that Cryostat is configured with
    (determined by the environment variable `GRAFANA_DATASOURCE_URL`).

    ###### request
    `POST /api/v1/recordings/:recordingName/upload`

    `recordingName` - The name of the saved recording to upload.
    Should use percent-encoding.

    ###### response
    `200` - The body is the body of the response that Cryostat got
    after sending the upload request to the Grafana datasource server.

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - The recording could not be found. The body is an error message.

    `500` - There was an unexpected error. The body is an error message.

    `501` - The Grafana datasource URL is malformed.
    The body is an error message.

    `502` - JMX connection failed. This is generally because the target
    application has SSL enabled over JMX, but Cryostat does not trust the
    certificate.

    `512` - Cryostat received an invalid response from the
    Grafana datasource after sending the upload request.
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
    `200` - The body is the requested report as an HTML document.

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - The report could not be found. The body is an error message.

    `500` - There was an error generating the report, such as: the report
    generation consumed too much memory and was aborted; an I/O failure occurred
    while transferring the report result; or an unexpected error occurred. The
    body is an error message.

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
    `{"name":"$NAME","typeId":"$TYPE_ID","description":"$DESCRIPTION","category":[$CATEGORIES],"options":{$OPTIONS}}`

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - The target could not be found. The body is an error message.

    `427` - JMX authentication failed. The body is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `500` - There was an unexpected error. The body is an error message.

    `502` - JMX connection failed. This is generally because the target
    application has SSL enabled over JMX, but Cryostat does not trust the
    certificate.

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

    `502` - JMX connection failed. This is generally because the target
    application has SSL enabled over JMX, but Cryostat does not trust the
    certificate.

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

    `502` - JMX connection failed. This is generally because the target
    application has SSL enabled over JMX, but Cryostat does not trust the
    certificate.

    ###### example
    ```
    $ curl localhost:8181/api/v1/targets/localhost/recordings/foo --output foo.jfr
      % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                     Dload  Upload   Total   Spent    Left  Speed
    100  530k    0  530k    0     0  9303k      0 --:--:-- --:--:-- --:--:-- 9303k
    ```

* #### `TargetRecordingOptionsGetHandler`

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

    `502` - JMX connection failed. This is generally because the target
    application has SSL enabled over JMX, but Cryostat does not trust the
    certificate.

    ###### example
    ```
    $ curl localhost:8181/api/v1/targets/localhost/recordingOptions
    {"maxAge":0,"toDisk":false,"maxSize":0}
    ```


* #### `TargetRecordingOptionsPatchHandler`

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

    **The request may include the following fields:**

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

    `502` - JMX connection failed. This is generally because the target
    application has SSL enabled over JMX, but Cryostat does not trust the
    certificate.

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
    variable `CRYOSTAT_ARCHIVE_PATH` can be used to specify a different
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

    `502` - JMX connection failed. This is generally because the target
    application has SSL enabled over JMX, but Cryostat does not trust the
    certificate.

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

    `502` - JMX connection failed. This is generally because the target
    application has SSL enabled over JMX, but Cryostat does not trust the
    certificate.

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

    `events` - The events configuration for the recording, as an
    event template using the form `template=$TEMPLATE`.

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

    `502` - JMX connection failed. This is generally because the target
    application has SSL enabled over JMX, but Cryostat does not trust the
    certificate.

    ###### example
    ```
    $ curl --data "recordingName=foo&duration=5&events=template=ALL" localhost:8181/api/v1/targets/localhost/recordings
    {"downloadUrl":"http://0.0.0.0:8181/api/v1/targets/service:jmx:rmi:%2F%2F%2Fjndi%2Frmi:%2F%2Flocalhost:9091%2Fjmxrmi/recordings/foo","reportUrl":"http://0.0.0.0:8181/api/v1/targets/service:jmx:rmi:%2F%2F%2Fjndi%2Frmi:%2F%2Flocalhost:9091%2Fjmxrmi/reports/foo","id":1,"name":"foo","state":"RUNNING","startTime":1599847667483,"duration":5000,"continuous":false,"toDisk":true,"maxSize":0,"maxAge":0}
    ```


* #### `TargetRecordingUploadPostHandler`

    ###### synopsis
    Uploads a recording of a target JVM to the Grafana datasource
    that Cryostat is configured with
    (determined by the environment variable `GRAFANA_DATASOURCE_URL`).

    ###### request
    `POST /api/v1/targets/:targetId/recordings/:recordingName/upload`

    `targetId` - The location of the target JVM to connect to,
    in the form of a `service:rmi:jmx://` JMX Service URL, or `hostname:port`.
    Should use percent-encoding.

    `recordingName` - The name of the recording to upload.
    Should use percent-encoding.

    ###### response
    `200` - The body is the body from the response that Cryostat got
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

    `502` - JMX connection failed. This is generally because the target
    application has SSL enabled over JMX, but Cryostat does not trust the
    certificate.

    `512` - Cryostat received an invalid response from the
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
    `200` - The body is the requested report as an HTML document.

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - The report could not be found, or the target could not be found.
    The body is an error message.

    `427` - JMX authentication failed. The body is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `500` - There was an error generating the report, such as: the report
    generation consumed too much memory and was aborted; the report generation
    process was unable to connect to the target; an I/O failure occurred while
    transferring the report result; or an unexpected error occurred. The body is
    an error message.

    `502` - JMX connection failed. This is generally because the target
    application has SSL enabled over JMX, but Cryostat does not trust the
    certificate.

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

    `502` - JMX connection failed. This is generally because the target
    application has SSL enabled over JMX, but Cryostat does not trust the
    certificate.

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

    `502` - JMX connection failed. This is generally because the target
    application has SSL enabled over JMX, but Cryostat does not trust the
    certificate.

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

    `502` - JMX connection failed. This is generally because the target
    application has SSL enabled over JMX, but Cryostat does not trust the
    certificate.

    ###### example
    ```
    $ curl localhost:8181/api/v1/targets/localhost/templates
    [{"name":"Profiling","description":"Low overhead configuration for profiling, typically around 2 % overhead.","provider":"Oracle","type":"TARGET"},{"name":"Continuous","description":"Low overhead configuration safe for continuous use in production environments, typically less than 1 % overhead.","provider":"Oracle","type":"TARGET"},{"name":"ALL","description":"Enable all available events in the target JVM, with default option values. This will be very expensive and is intended primarily for testing Cryostat's own capabilities.","provider":"Cryostat","type":"TARGET"}]
    ```


* #### `TemplateDeleteHandler`

    ###### synopsis
    Deletes an event template that was uploaded to Cryostat and is stored
    in `CRYOSTAT_TEMPLATE_PATH`.

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

    `500` - `CRYOSTAT_TEMPLATE_PATH` is not set,
    or it is set to an invalid path, or there was an unexpected error.
    The body is an error message.

    ###### example
    ```
    $ curl -X DELETE localhost:8181/api/v1/templates/Foo
    ```


* #### `TemplatesPostHandler`

    ###### synopsis
    Uploads an event template and makes it available for use by Cryostat.
    The template is saved to the path specified by
    `CRYOSTAT_TEMPLATE_PATH`.

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

    `500` - `CRYOSTAT_TEMPLATE_PATH` is not set,
    or it is set to an invalid path, or there was an unexpected error.
    The body is an error message.

    ###### example
    ```
    $ curl --form "template=@Foo.jfc" localhost:8181/api/v1/templates
    ```

## V2 API

Unless otherwise specified, all V2 API handlers respond to requests with a
metadata-wrapped and JSON-encoded response format with the following general
form:

```
{ "meta": { "status": "statusString", "type": "mime/type"}, "data": { "someKey": someValue } }
```

`statusString` will always be `OK` if the response status code is `200`. If the
response status code is `4xx` or `5xx` then `statusString` may match the HTTP
response status message, or it may provide more detail specific to the handler
and the exact cause of failure.

`type` is a MIME type specifier (ex. `application/json`, `text/plain`) which
hints to the client how the response `data` is formatted. The `data` key will
always be included and the value will always be an object, but the contents of
the object vary by handler.

`someKey` may be named anything. The most common name on successful requests is
`result`, but this may vary by handler. The associated value may be anything and
varies by handler. It may be a plain text string, or a next object, or an array.

The response format on failure is similar:

```
{"meta":{"type":"text/plain","status":"Authentication Failure"},"data":{"reason":"JMX Authentication Failure"}}
```

Any response with a `4xx` or `5xx` status code will have `someKey` replaced by
`reason` as explained above, and the value of `reason` will be a plain text
string containing some additional detail about the failure.

The handler-specific descriptions below describe how each handler populates the
`type` and `data` fields. The general formats follow as above.

### Quick Reference

| What you want to do                                                       | Which handler you should use                                                    |
| ------------------------------------------------------------------------- | --------------------------------------------------------------------------------|
| **Recordings in Target JVMs**                                             |                                                                                 |
| Search event types that can be produced by a target JVM                   | [`TargetEventsSearchGetHandler`](#TargetEventsSearchGetHandler)                 |
| Get a list of recording options for a target JVM                          | [`TargetRecordingOptionsListGetHandler`](#TargetRecordingOptionsListGetHandler) |
| Create a snapshot recording in a target JVM                               | [`TargetSnapshotPostHandler`](#TargetSnapshotPostHandler-1)                     |
| **Automated Rules**                                                       |                                                                                 |
| Create an automated rule definition                                       | [`RulesPostHandler`](#RulesPostHandler)                                         |
| Delete an automated rule definition                                       | [`RuleDeleteHandler`](#RuleDeleteHandler)                                       |
| Get an automated rule definition                                          | [`RuleGetHandler`](#RuleGetHandler)                                             |
| Get all automated rule definitions                                        | [`RulesGetHandler`](#RulesGetHandler)                                           |
| **Stored Target Credentials**                                             |                                                                                 |
| Add stored credentials for a target                                       | [`TargetCredentialsPostHandler`](#TargetCredentialsPostHandler)                 |
| Delete stored credentials for a target                                    | [`TargetCredentialsDeleteHandler`](#TargetCredentialsDeleteHandler)             |
| **Security**                                                              |                                                                                 |
| Upload an SSL Certificate                                                 | [`CertificatePostHandler`](#CertificatePostHandler)                             |

### Recordings in Target JVMs

* #### `TargetEventsSearchGetHandler`

    ###### synopsis
    Returns a list of event types that can be produced by a target JVM,
    where the event name, category, label, etc. matches the given query.

    ###### request
    `GET /api/v2/targets/:targetId/eventsSearch/:query`

    `targetId` - The location of the target JVM to connect to,
    in the form of a `service:rmi:jmx://` JMX Service URL, or `hostname:port`.
    Should use percent-encoding.

    `query` - The search query.

    ###### response
    `200` - The result is a JSON array of event objects.

    The format of an event is
    `{"name":"$NAME","typeId":"$TYPE_ID","description":"$DESCRIPTION","category":[$CATEGORIES],"options":{$OPTIONS}}`.

    `401` - User authentication failed. The reason is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - The target could not be found. The reason is an error message.

    `427` - JMX authentication failed. The reason is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `500` - There was an unexpected error. The reason is an error message.

    `502` - JMX connection failed. This is generally because the target
    application has SSL enabled over JMX, but Cryostat does not trust the
    certificate.

    ###### example
    ```
    $ curl localhost:8181/api/v2/targets/localhost/eventsSearch/javaerrorthrow
    {"meta":{"type":"application/json","status":"OK"},"data":{"result":[{"name":"Java Error","typeId":"jdk.JavaErrorThrow","description":"An object derived from java.lang.Error has been created. OutOfMemoryErrors are ignored","category":["Java Application"],"options":{"enabled":{"name":"Enabled","description":"Record event","defaultValue":"false"},"threshold":{"name":"Threshold","description":"Record event with duration above or equal to threshold","defaultValue":"0ns[ns]"},"stackTrace":{"name":"Stack Trace","description":"Record stack traces","defaultValue":"false"}}}]}}
    ```

* #### `TargetRecordingOptionsListGetHandler`

    ###### synopsis
    Returns a list of recording options which may be set for recordings within
    a target JVM. Not all options are guaranteed to be supported by the client.

    ###### request
    `GET /api/v2/targets/:targetId/recordingOptionsList`

    `targetId` - The location of the target JVM to connect to,
    in the form of a `service:rmi:jmx://` JMX Service URL, or `hostname:port`.
    Should use percent-encoding.

    ###### response
    `200` - The result is a JSON array of recording option objects.

    The format of a recording option is
    `{"name":"$NAME","description":"$DESCRIPTION","defaultValue":"$DEFAULT"}`.

    `401` - User authentication failed. The reason is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - The target could not be found. The reason is an error message.

    `427` - JMX authentication failed. The reason is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `500` - There was an unexpected error. The reason is an error message.

    `502` - JMX connection failed. This is generally because the target
    application has SSL enabled over JMX, but Cryostat does not trust the
    certificate.

    ###### example
    ```
    $ curl localhost:8181/api/v2/targets/localhost/recordingOptionsList
    {"meta":{"status":"OK","type":"application/json"},"data":{result:[{"name":"Name","description":"Recording name","defaultValue":"Recording"},{"name":"Duration","description":"Duration of recording","defaultValue":"30s[s]"},{"name":"Max Size","description":"Maximum size of recording","defaultValue":"0B[B]"},{"name":"Max Age","description":"Maximum age of the events in the recording","defaultValue":"0s[s]"},{"name":"To disk","description":"Record to disk","defaultValue":"false"},{"name":"Dump on Exit","description":"Dump recording data to disk on JVM exit","defaultValue":"false"}]}}
    ```

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
    `201` - The response is a descriptor of the newly created recording, in the form
    `{"downloadUrl":"$DOWNLOAD_URL","reportUrl":"$REPORT_URL","id":$ID,"name":"$NAME","state":"$STATE","startTime":$START_TIME,"duration":$DURATION,"continuous":$CONTINUOUS,"toDisk":$TO_DISK,"maxSize":$MAX_SIZE,"maxAge":$MAX_AGE}`. The `Location` header will also be set
    to the same URL as in the `downloadUrl` field.

    `401` - User authentication failed. The reason is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - The target could not be found. The reason is an error message.

    `427` - JMX authentication failed. The reason is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `500` - There was an unexpected error. The reason is an error message.

    `502` - JMX connection failed. This is generally because the target
    application has SSL enabled over JMX, but Cryostat does not trust the
    certificate.

    ###### example
    ```
    $ curl -X POST localhost:8181/api/v2/targets/localhost/snapshot
    {"meta":{"status":"Created","type":"application/json"},"data":{"result":{"downloadUrl":"http://192.168.0.109:8181/api/v1/targets/service:jmx:rmi:%2F%2F%2Fjndi%2Frmi:%2F%2Flocalhost:9091%2Fjmxrmi/recordings/snapshot-1","reportUrl":"http://192.168.0.109:8181/api/v1/targets/service:jmx:rmi:%2F%2F%2Fjndi%2Frmi:%2F%2Flocalhost:9091%2Fjmxrmi/reports/snapshot-1","id":1,"name":"snapshot-1","state":"STOPPED","startTime":1601998841300,"duration":0,"continuous":true,"toDisk":true,"maxSize":0,"maxAge":0}}}
    ```

### Automated Rules

* #### `RulesPostHandler`

    ##### synopsis
    Creates a new automated rule definition. Cryostat processes automated rules
    to start recordings on matching targets as they appear, non-interactively.
    Newly-POSTed rule definitions will also retroactively apply to already-known
    targets, if any.

    ##### request
    `POST /api/v2/rules`

    The request may be an HTTP form or a JSON document. In either case, the
    attributes `"name"`, `"matchExpression"`, and `"eventSpecifier"` must be
    provided.

    `"name"`: the name of this rule definition. This must be unique. This name
    will also be used to generate the name of the associated recordings.

    `"matchExpression"`: a string expression used to determine which target JVMs
    this rule will apply to. The expression has a variable named `target` in
    scope, which is of type
    [`ServiceRef`](src/main/java/io/cryostat/platform/ServiceRef.java).
    Properties can be accessed using `.` separators, and the operators `==`,
    `!=`, `||`, and `&&` are accepted, with their usual meanings. An example of
    such an expression is:
    `(target.alias == 'io.cryostat.Cryostat' || target.annotations.cryostat.JAVA_MAIN == 'io.cryostat.Cryostat') && target.annotations.cryostat.PORT != 9091`.
    The simple expression `true` may also be used to create a rule which applies
    to any and all discovered targets.

    `"eventSpecifier"`: a string of the form `template=Foo,type=TYPE`. This
    defines the event template that will be used for creating new recordings in
    matching targets.

    The following attributes are optional:

    `"description"`: a textual description of the purpose or reason for this
    rule definition. This is informational and for display only.

    `"archivalPeriodSeconds"`: a positive integer value that defines how long
    Cryostat should wait, in seconds, between archiving snapshots of the
    recording. The default setting is 30.

    `"preservedArchives"`: a positive integer value that defines how many
    archived copies of the recording should be kept in storage. When the number
    of archived copies exceeds this number the oldest copies are deleted from
    storage. The default setting is 1.

    `"maxAgeSeconds"`: a positive integer value that defines the maximum age of
    data to be retained in the active, in-memory Flight Recording within the
    Target JVM. This can be used in combination with `"archivalPeriodSeconds"`
    to minimize or eliminate data overlap between the end of one archived
    recording and the start of the subsequent archived recording. If not
    specified, the default setting is equal to `"archivalPeriodSeconds"`.

    `"maxSizeBytes"`: a positive integer value that defines the maximum size, in
    bytes, of the active in-memory Flight Recording within the Target JVM. If
    the recording exceeds this memory size then event data will be dropped from
    the recording. The default setting is unlimited.

    ##### response
    `201` - The result is the name of the created rule. The `LOCATION` header
    will be set and its value will be the relative path to the created resource.

    `400` - The rule definition could not be processed, either because the
    provided document was malformed or invalid.

    `401` - User authentication failed. The reason is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `409` - A rule with the same name already exists.

    `415` - The request's `Content-Type` was invalid or unrecognized.

    `500` - There was an unexpected error.

    ##### example
    ```
    $ curl -X POST -F name="Test Rule" -F description="This is a rule for testing" -F matchExpression="target.alias == 'io.cryostat.Cryostat'" -F eventSpecifier="template=Continuous,type=TARGET" http://0.0.0.0:8181/api/v2/rules
    < HTTP/1.1 201 Created
    < location: /api/v2/rules/Test_Rule
    < content-length: 79
    {"meta":{"type":"text/plain","status":"Created"},"data":{"result":"Test_Rule"}}
    ```

* #### `RuleDeleteHandler`

    ##### synopsis
    Deletes a rule definition.

    ##### request
    `DELETE /api/v2/rules/:name`

    ##### response
    `200` - The result is empty. The rule was successfully deleted.

    `401` - User authentication failed. The reason is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - No rule with the given name exists.

    `500`- An unexpected IOException occurred while deleting the rule
    definition.

    ##### example
    ```
    $ curl -X DELETE http://0.0.0.0:8181/api/v2/rules/Test_Rule
    {"meta":{"type":"text/plain","status":"OK"},"data":{"result":null}}
    ```

* #### `RuleGetHandler`

    ##### synopsis
    Get a JSON document describing a rule definition with the given name.

    ##### request
    `GET /api/v2/rules/:name`

    ##### response
    `200` - The result is a JSON string representing the rule definition.

    `401` - User authentication failed. The reason is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - No rule with the given name exists.

    `500` - There was an unexpected error.

    ##### example
    ```
    $ curl http://0.0.0.0:8181/api/v2/rules/Test_Rule
    {"meta":{"type":"application/json","status":"OK"},"data":{"result":{"name":"Test_Rule","description":"This is a rule for testing","matchExpression":"target.alias=='io.cryostat.Cryostat'","eventSpecifier":"template=Continuous,type=TARGET","archivalPeriodSeconds":30,"preservedArchives":1,"maxAgeSeconds":30,"maxSizeBytes":-1}}}
    ```

* #### `RulesGetHandler`

    ##### synopsis
    Get a JSON array representing all rule definitions.

    ##### request
    `GET /api/v2/rules`

    ##### response
    `200` - The result is a JSON array representing all rule definitions.

    `401` - User authentication failed. The reason is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `500` - There was an unexpected error.

    ##### example
    ```
    $ curl http://0.0.0.0:8181/api/v2/rules
    {"meta":{"type":"application/json","status":"OK"},"data":{"result":[{"name":"Test_Rule","description":"This is a rule for testing","matchExpression":"target.alias=='io.cryostat.Cryostat'","eventSpecifier":"template=Continuous,type=TARGET","archivalPeriodSeconds":30,"preservedArchives":1,"maxAgeSeconds":30,"maxSizeBytes":-1}]}}    ```

### Stored Target Credentials

* #### `TargetCredentialsPostHandler`

    ##### synopsis
    Creates stored credentials for a given target. These are used for automated
    rules processing - if a Target JVM requires JMX authentication, Cryostat
    will use stored credentials when attempting to open JMX connections to the
    target. These are retained in Cryostat's memory only and not persisted to
    disk.

    ##### request
    `POST /api/v2/targets/:targetId/credentials`

    The request should be an HTTP form with the attributes `"username"` and
    `"password"`. Both are required.

    ##### response
    `200` - The result is null. The request was processed successfully and the
    credentials were stored, potentially overriding previous credentials for the
    same target.

    `400` - `"username"` and/or `"password"` were not provided.

    `401` - User authentication failed. The reason is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `500` - There was an unexpected error.

    ##### example
    ```
    $ curl -F username=user -F password=pass http://0.0.0.0:8181/api/v2/targets/localhost/credentials
    {"meta":{"type":"text/plain","status":"OK"},"data":{"result":null}}
    ```

* #### `TargetCredentialsDeleteHandler`

    ##### synopsis
    Deletes stored credentials for a given target.

    ##### request
    `DELETE /api/v2/targets/:targetId/credentials`

    ##### response
    `200` - The result is null. The request was processed successfully and the
    credentials were deleted.

    `404` - The target had no stored credentials.

    `401` - User authentication failed. The reason is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `500` - There was an unexpected error.

    ##### example
    ```
    $ curl -X DELETE http://0.0.0.0:8181/api/v2/targets/localhost/credentials
    {"meta":{"type":"text/plain","status":"OK"},"data":{"result":null}}
    ```

### Security

* #### `CertificatePostHandler`

    ###### synopsis
    Uploads an SSL Certificate from the client, and saves it to the truststore directory.

    ###### request
    `POST /api/v2/certificates`

    The certificate must be DER-encoded and can be either binary or base64. The supported extensions are .der, .cer, .pem.
    The certificate should be uploaded in a form with the name `cert`.

    ###### response
    `200` - The result is the path of the saved file in the server's storage.

    `400` - No `cert` was found in the request form. The reason is the error message `A file named "cert" was not included in the request`.

    `409` - A certificate with the same filename already exists in the truststore directory. The reason includes the path where the file already exists.

    `500` - The `SSL_TRUSTSTORE_DIR` environment variable is not set, or there is an unexpected error. The reason is an error message.

    ###### example
    ```
    $ curl -F cert=@vertx-fib-demo.cer localhost:8181/api/v2/certificates
    {"meta":{"type":"text/plain","status":"OK"},"data":{"result":"/truststore/vertx-fib-demo.cer"}}
    ```
