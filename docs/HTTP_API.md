# HTTP API

* [V1](#V1-API)
* [V2](#V2-API)
* [Beta](#Beta-API)

## V1 API

### Quick Reference

| What you want to do                                                       | Which handler you should use                                                |
| ------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| **Miscellaneous**                                                         |                                                                             |
| Get a URL you can use to access Cryostat's WebSocket notification channel | [`NotificationsUrlGetHandler`](#NotificationsUrlGetHandler)                 |
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
    [`README.md`](../README.md#user-authentication--authorization).

    ###### response
    `200` - No body. Getting this response means that the header is valid
    and that the user has been successfully authenticated. There will be an
    `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.
    Getting this response means that the header has an invalid format
    or the user has not been successfully authenticated.

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

    ###### example
    ```
    $ curl localhost:8181/api/v1/grafana_datasource_url
    {"grafanaDatasourceUrl":"http://localhost:8080"}
    ```


* #### `HealthGetHandler`

    ###### synopsis
    Returns whether or not the Grafana datasource, Grafana dashboard, and
    reports generator components are configured and reachable by Cryostat.
    Can also be used to see if Cryostat itself is running properly
    by checking for a valid response.

    ###### request
    `GET /health`

    ###### response
    `200` - The body is
    ```
        {
          "cryostatVersion": "$CRYOSTAT_VERSION",
          "datasourceConfigured": $DATASOURCE_CONFIGURED,
          "datasourceAvailable": $DATASOURCE_AVAILABLE,
          "dashboardConfigured": $DASHBOARD_CONFIGURED,
          "dashboardAvailable": $DASHBOARD_AVAILABLE,
          "reportsConfigured": $REPORTS_CONFIGURED,
          "reportsAvailable": $REPORTS_AVAILABLE
        }
    ```
    `$CRYOSTAT_VERSION` is the version of the current Cryostat instance.

    `$DATASOURCE_CONFIGURED` is `true` if the relevant environment variable has
    been set to a non-empty value.

    `$DATASOURCE_AVAILABLE` is `true` if  Cryostat is configured with a
    Grafana datasource and that datasource responds to a `GET` request
    with a `200`, and it is `false` otherwise.

    `$DASHBOARD_CONFIGURED` is `true` if the relevant environment variable has
    been set to a non-empty value.

    `$DASHBOARD_AVAILABLE` is `true` if  Cryostat is configured with a
    Grafana dashboard and that dashboard responds to a `GET` request
    with a `200`, and it is `false` otherwise.

    `$REPORTS_CONFIGURED` is `true` if the relevant environment variable has
    been set to a non-empty value.

    `$REPORTS_AVAILABLE` is `true` if Cryostat is configured with a sidecar
    reports generator and that generator responds to a `GET` request with a
    `200`. It is also `true` if no reports sidecard is configured, since in this
    case Cryostat will fall back to forking a subprocess to generate reports. It
    is only `false` if a sidecar report generator is configured but is not
    reachable.

    ###### example
    ```
    $ curl localhost:8181/health
    {"dashboardConfigured":false,"dashboardAvailable":false,"datasourceConfigured":false,"datasourceAvailable":false,"reportsConfigured":false,"reportsAvailable":true}
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
    **DEPRECATED**: Endpoints treating the archived recording storage as
    uncategorized storage, where files are not associated with a particular
    target application, are deprecated and will be removed in a future release.
    See [`RecordingDeleteHandler`](#RecordingDeleteHandler-1).

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

    ###### example
    ```
    $ curl -X DELETE localhost:8181/api/v1/recordings/localhost_foo_20200910T213341Z.jfr
    ```


* #### `RecordingGetHandler`

    ###### synopsis
    Returns a recording that was saved to archive,
    as an octet stream.
    **DEPRECATED**: Endpoints treating the archived recording storage as
    uncategorized storage, where files are not associated with a particular
    target application, are deprecated and will be removed in a future release.
    See [`RecordingGetHandler`](#RecordingGetHandler-2).

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
    **DEPRECATED**: Endpoints treating the archived recording storage as
    uncategorized storage, where files are not associated with a particular
    target application, are deprecated and will be removed in a future release.

    ###### request
    `GET /api/v1/recordings`

    ###### response
    `200` - The body is a JSON array of recording objects.

    The format for a recording is
    `{"downloadUrl":"$DOWNLOAD_URL","name":"$NAME","reportUrl":"$REPORT_URL","metadata":"{"labels":{"$KEY":"$VALUE"}},"size":"$SIZE"`.

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `501` - The archive path where recordings are saved could not be accessed.
    The body is an error message.

    ###### example
    ```
    $ curl localhost:8181/api/v1/recordings
    [{"downloadUrl":"http://192.168.0.109:8181/api/v1/recordings/localhost_foo_20200903T202547Z.jfr","metadata":{"labels":{"template.name":"Continuous","template.type": "TARGET"}},"name":"localhost_foo_20200903T202547Z.jfr","reportUrl":"http://192.168.0.109:8181/api/v1/reports/localhost_foo_20200903T202547Z.jfr","size":2578134}]
    ```


* #### `RecordingsPostHandler`

    ###### synopsis
    Uploads a recording from the client to Cryostat's archive.
    **DEPRECATED**: Endpoints treating the archived recording storage as
    uncategorized storage, where files are not associated with a particular
    target application, are deprecated and will be removed in a future release.

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
    **DEPRECATED**: Endpoints treating the archived recording storage as
    uncategorized storage, where files are not associated with a particular
    target application, are deprecated and will be removed in a future release.
    See [`RecordingUploadPostHandler`](#RecordingUploadPostHandler-1).

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
    **DEPRECATED**: Endpoints treating the archived recording storage as
    uncategorized storage, where files are not associated with a particular
    target application, are deprecated and will be removed in a future release.
    See [`RecordingGetHandler`](#RecordingGetHandler-2).

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
    either `true` or `false`, or `unset` to restore the JVM default.

    **The request may include the following fields:**

    `maxAge` - The maximum event age of a recording, in seconds as a positive
    integer, or `unset` to restore the JVM default.

    `maxSize` - The maximum size of a recording, in bytes as a positive integer,
    or `unset` to restore the JVM default.

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

    `502` - JMX connection failed. This is generally because the target
    application has SSL enabled over JMX, but Cryostat does not trust the
    certificate.

    ###### example
    ```
    $ curl -X PATCH --data "toDisk=unset&maxAge=60&maxSize=1024" localhost:8181/api/v1/targets/localhost/recordingOptions
    {"maxAge":60,"toDisk":"unset","maxSize":1024}
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

    `502` - JMX connection failed. This is generally because the target
    application has SSL enabled over JMX, but Cryostat does not trust the
    certificate.

    **STOP**

    `200` - No body.

    **SAVE**

    `200` - The body is the name of the recording that was saved.
    Note that this name will be different from the recording's original name,
    to add metadata.

    `204` - The recording did not contain any data to archive.

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

    `restart`: Whether to restart the recording if one already exists with the same name. **DEPRECATED**: See `replace` below.

    `replace`: The replacement policy if a recording already exists with the same name. Policies can be `ALWAYS` (i.e. `restart=true`), `NEVER` (i.e.`restart=false`), and `STOPPED` (restart only when the existing one is stopped).

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

    `metadata` - A JSON object containing metadata about the recording. `metadata` should contain a `labels` object with `"key": "value"` string pairs, e.g. `metadata={"labels":{"reason":"service-outage"}}`.

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

    `202` - The request was accepted but the recording failed to create
    because the resultant snapshot was unreadable. This could be due
    to a lack of active recordings to take event data from.

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - The target could not be found. The body is an error message.

    `427` - JMX authentication failed. The body is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

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
| **Miscellaneous**                                                         |                                                                                 |
| Check user authentication                                                 | [`AuthPostHandler`](#AuthPostHandler-1)                                         |
| Generate JWT for interactive asset download                               | [`AuthTokenPostHandler`](#AuthTokenPostHandler)                                 |
| Perform batched start/stop/delete operations across target JVMs           | [`GraphQLHandler`](#GraphQLHandler)                                             |
| Check the status of Cryostat itself                                       | [`HealthLivenessGetHandler`](#HealthLivenessGetHandler)                         |
| **Target JVMs**                                                           |                                                                                 |
| Add a custom target definition                                            | [`TargetsPostHandler`](#TargetsPostHandler)                                     |
| Delete a custom target definition                                         | [`TargetDeleteHandler`](#TargetDeleteHandler)                                   |
| **Target Discovery**                                                      |                                                                                 |
| View targets in overall deployment environment                            | [`DiscoveryGetHandler`](#DiscoveryGetHandler)                                   |
| Register a discovery plugin                                               | [`DiscoveryRegistrationHandler`](#DiscoveryRegistrationHandler)                 |
| Update discovered scenario                                                | [`DiscoveryPostHandler`](#DiscoveryPostHandler)                                 |
| Deregister a discovery plugin                                             | [`DiscoveryDeregistrationHandler`](#DiscoveryDeregistrationHandler)             |
| **Events and event templates**                                            |                                                                                 |
| Download a template from a target JVM                                     | [`TargetTemplateGetHandler`](#TargetTemplateGetHandler-1)                       |
| **Recordings in Target JVMs**                                             |                                                                                 |
| List or search event types that can be produced by a target JVM           | [`TargetEventsGetHandler`](#TargetEventsGetHandler)                             |
| Get a list of recording options for a target JVM                          | [`TargetRecordingOptionsListGetHandler`](#TargetRecordingOptionsListGetHandler) |
| Download a recording in a target JVM                                      | [`TargetRecordingGetHandler`](#TargetRecordingGetHandler-1)                     |
| Download a report of a recording in a target JVM                          | [`TargetReportGetHandler`](#TargetReportGetHandler-1)                           |
| Create a snapshot recording in a target JVM                               | [`TargetSnapshotPostHandler`](#TargetSnapshotPostHandler-1)                     |
| **Recordings in archive**                                                 |                                                                                 |
| Download a recording in archive                                           | [`RecordingGetHandler`](#RecordingGetHandler-1)                                 |
| Download a report of a recording in archive                               | [`ReportGetHandler`](#ReportGetHandler-1)                                       |
| **Automated Rules**                                                       |                                                                                 |
| Create an automated rule definition                                       | [`RulesPostHandler`](#RulesPostHandler)                                         |
| Delete an automated rule definition                                       | [`RuleDeleteHandler`](#RuleDeleteHandler)                                       |
| Get an automated rule definition                                          | [`RuleGetHandler`](#RuleGetHandler)                                             |
| Get all automated rule definitions                                        | [`RulesGetHandler`](#RulesGetHandler)                                           |
| **Stored Target Credentials**                                             |                                                                                 |
| Add stored credentials for a target                                       | [`TargetCredentialsPostHandler`](#TargetCredentialsPostHandler)                 |
| Delete stored credentials for a target                                    | [`TargetCredentialsDeleteHandler`](#TargetCredentialsDeleteHandler)             |
| Get a list of targets with stored credentials                             | [`TargetCredentialsGetHandler`](#TargetCredentialsGetHandler)                   |
| Add stored credentials                                                    | [`CredentialsPostHandler`](#CredentialsPostHandler)                             |
| Get a list of stored credentials                                          | [`CredentialsGetHandler`](#CredentialsGetHandler)                               |
| Get a stored credential and its matching targets                          | [`CredentialGetHandler`](#CredentialGetHandler)                                 |
| Delete stored credentials                                                 | [`CredentialDeleteHandler`](#CredentialDeleteHandler)                           |
| **Security**                                                              |                                                                                 |
| Upload an SSL Certificate                                                 | [`CertificatePostHandler`](#CertificatePostHandler)                             |


### Miscellaneous

* #### `AuthPostHandler`

    ##### synopsis
    Check user authentication and if successful retrieve basic user information.

    ##### request
    `POST /api/v2/auth`

    The `Authorization` header should be included. The value of the
    `Authorization` header will be used to perform a platform-specific
    authentication check for the user specified by the header. If using `Basic`
    authentication, the returned user information will contain a username
    identical to the user portion of the provided credentials. If using `Bearer`
    authentication, the returned user information will container the username of
    the user who owns the supplied Bearer token.

    ##### response
    `200` - The result is a JSON object containing user information. The format
    of the user information is `{"username": "$user"}`. There will be an
    `X-WWW-Authenticate: $SCHEME` header that indicates the authentication
    scheme that is used.

    `401` - User authentication failed. The reason is an error message. There
    will be an `X-WWW-Authenticate: $SCHEME` header that indicates the
    authentication scheme that is used.

    ##### example
    ```
    $ curl -H "Authorization: Basic $(echo -n user:pass | base64)" -X POST localhost:8181/api/v2/auth
    {"meta":{"type":"application/json","status":"OK"},"data":{"result":{"username":"user"}}}
    ```

* #### `AuthTokenPostHandler`

    #### synopsis
    Generates JSON Web Tokens (JWTs) to be consumed by other asset-downloading
    V2+ API endpoints such as the `TargetTemplateGetHandler`. These endpoints
    are only intended for use by interactive clients such as web browsers, where
    separating the authorization check from the actual asset transfer may be
    beneficial. For programmatic and automated endpoints the non-JWT endpoints
    may be used and are likely preferable.

    #### request
    `POST /api/v2.1/auth/token`

    The request is an HTTP POST form. The form attribute `resource` is required.
    This attribute specifies the URL path of the desired final resource to be
    retrieved, ex. `/api/v2.1/targets/localhost:0/recordings/myrecording`.

    #### response
    `200` - The request was accepted and the response contains a relative
    request URL. The client should use this URL in a new request to obtain the
    desired final resource. The URL is already formatted to include the JWT.

    `400` - The final resource URL was invalid.

    `401` - The user does not have sufficient permissions.

    #### example
    ```
    $ curl -k 'https://localhost:8181/api/v2.1/auth/token' -X POST -H 'authorization: Basic dXNlcjpwYXNz' --data resource="/api/v2.1/targets/localhost:0/templates/Continuous/type/TARGET"
    {"meta":{"type":"application/json","status":"OK"},"data":{"result":{"resourceUrl":"/api/v2.1/targets/localhost:0/templates/Continuous/type/TARGET?token=(trimmed)"}}}
    ```

* #### `GraphQLHandler`

    ##### synopsis
    Performs various queries using the GraphQL request syntax and response
    format. See also [GRAPHQL.md](GRAPHQL.md). Requests can query for target
    JVMs, active and archived recordings, by name, labels, and other properties.
    A single request can, for example, start identical recordings with one
    configuration against all target JVMs that have a given label.

    ##### request
    `POST /api/v2.2/graphql`, the body containing the GraphQL query
    `GET /api/v2.2/graphql?query=myquery`, where `myquery` is the GraphQL query

    ##### response
    `200` - The result is the GraphQL query response in JSON format.

    `401` - The user does not have sufficient permissions. Permissions for each
    subquery or submutation are checked on-demand as the request is processed,
    and the request will fail with this status if any of the nested
    queries/mutations fails the authorization check.

* #### `HealthLivenessGetHandler`

    ###### synopsis
    Returns whether or not Cryostat itself is running properly
    by checking for a valid No Content response (since: 2.2.0).

    ###### request
    `GET /health/liveness`

    ###### response
    `204` - There is no content to display.

    ###### example
    ```
    $ curl localhost:8181/health/liveness
    ```

### Target JVMs

* #### `TargetsPostHandler`

    ##### synopsis
    Add or test a custom target definition to connect to Cryostat.

    ##### request
    `POST /api/v2/targets[?dryrun=true]`

    `dryrun` - optional. If set to "true", test whether the target definition is valid.

    The request should be an HTTP form. The
    attribute `connectUrl` must be specified.

    `connectUrl` - The target connection URL formatted as a JMX service URL or `host:port` pair.

    `alias` - An optional name for the target.

    `annotations.cryostat` - Optional annotations used by [Automated Rules](#RulesPostHandler) for selecting targets. The following annotations can be specified:
    * annotations.cryostat.HOST
    * annotations.cryostat.PORT
    * annotations.cryostat.JAVA_MAIN
    * annotations.cryostat.PID
    * annotations.cryostat.START_TIME
    * annotations.cryostat.NAMESPACE
    * annotations.cryostat.SERVICE_NAME
    * annotations.cryostat.CONTAINER_NAME
    * annotations.cryostat.POD_NAME

    `username` - the username for the target with JMX Authentication enabled.

    `password` - the password for the target with JMX Authentication enabled.

    ##### response
    `200` - The result is a JSON object containing information about the target. The format
    of the target data is `{"connectUrl":"$CONNECTURL","alias":"$ALIAS","annotations":{"platform": {$PLATFORM_ANNOTATIONS},"cryostat":{$CRYOSTAT_ANNOTATIONS}}`. `$PLATFORM_ANNOTATIONS` are automatically generated.

    `400` - An argument was invalid. The body is an error message.

    `401` - User authentication failed. The reason is an error message. There
    will be an `X-WWW-Authenticate: $SCHEME` header that indicates the
    authentication scheme that is used.

    `404` - The target could not be found. The body is an error message.

    `427` - JMX authentication failed. The body is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `502` - JMX connection failed. This is generally because the target
    application has SSL enabled over JMX, but Cryostat does not trust the
    certificate.

    `504` - JMX connection failed. This occurs when the port provided in `connectUrl` is a non-JMX port.

    ##### example
    ```
    $ curl -F connectUrl=service:jmx:rmi:///jndi/rmi://cryostat:9099/jmxrmi -F alias=fooTarget -F annotations.cryostat.PORT=9099 -F username="user" -F password="pass" -X POST https://0.0.0.0:8181/api/v2/targets {"meta":{"type":"application/json","status":"OK"},"data":{"result":{"connectUrl":"service:jmx:rmi:///jndi/rmi://cryostat:9099/jmxrmi","alias":"fooTarget","annotations":{"platform":{},"cryostat":{"PORT":"9099"}}}}}
    ```

* #### `TargetDeleteHandler`

    ##### synopsis
    Remove a custom target definition.

    ##### request
    `DELETE /api/v2/targets/:connectUrl`

    `connectUrl` - The target connection URL formatted as a URL-encoded JMX service URL or `host:port` pair.

    ##### response
    `200` - The result is empty. The custom target definition was successfully deleted.

    `401` - User authentication failed. The reason is an error message. There
    will be an `X-WWW-Authenticate: $SCHEME` header that indicates the
    authentication scheme that is used.

    `404` - The target could not be found. The body is an error message.

    ##### example
    ```
    $ curl -X DELETE https://0.0.0.0:8181/api/v2/targets/service%3Ajmx%3Armi%3A%2F%2F%2Fjndi%2Frmi%3A%2F%2Fcryostat%3A9099%2Fjmxrmi
    {"meta":{"type":"application/json","status":"OK"},"data":{"result":null}}
    ```

### Target Discovery

* #### `DiscoveryGetHandler`

    ###### synopsis
    Collates the deployment scenarios discovered by the built-in discovery
    mechanisms and any registered discovery plugins into a hierarchical tree
    view of the full deployment environment with targets as leaf nodes belonging
    to ex. Pods, belonging to Deployments, belonging to a Namespace etc. The
    root of the tree is always the `UNIVERSE` node. The `UNIVERSE`'s children
    are `REALM`s, some of which are provided by Cryostat's built-in
    platform-specific discovery mechanisms and others which are provided by
    discovery plugins. The subtrees below the `REALM` are specific to the
    platform mechanism or discovery plugin.

    ###### request
    `GET /api/v2.1/discovery`

    ###### response
    `200` - The result is the hierarchical tree view as JSON.
    ```json
    {
        "data": {
            "result": {
                "children": [
                    {
                        "children": [],
                        "labels": {},
                        "name": "Custom Targets",
                        "nodeType": "Realm"
                    },
                    {
                        "children": [
                            {
                                "labels": {},
                                "name": "service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi",
                                "nodeType": "JVM",
                                "target": {
                                    "alias": "io.cryostat.Cryostat",
                                    "annotations": {
                                        "cryostat": {
                                            "HOST": "cryostat",
                                            "JAVA_MAIN": "io.cryostat.Cryostat",
                                            "PORT": "9091",
                                            "REALM": "JDP"
                                        },
                                        "platform": {}
                                    },
                                    "connectUrl": "service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi",
                                    "labels": {}
                                }
                            }
                        ],
                        "labels": {},
                        "name": "JDP",
                        "nodeType": "Realm"
                    }
                ],
                "labels": {},
                "name": "Universe",
                "nodeType": "Universe"
            }
        },
        "meta": {
            "status": "OK",
            "type": "application/json"
        }
    }
    ```

    `401` - The user does not have sufficient permissions.

* #### `DiscoveryRegistrationHandler`

    ###### synopsis
    Registers a discovery plugin. Plugins must be registered before they can
    publish updates. Request must include a JSON body with a `realm` and a
    `callback`. The `realm` is a display name label for the subtree that this
    plugin will publish and may not be blank. The `callback` is a URI to an
    endpoint where Cryostat can reach the discovery plugin to perform health
    checks. The plugin may optionally include an `id` and a `token` in the JSON
    body. This fields are expected to be the `id` and `token` that the plugin
    previously received from Cryostat after an earlier registration. This token
    is opaque to the client but contains an expiry time known to Cryostat, a
    well as authorization information. If the request includes a prior `id` and
    `token` then rather than processing the request as a new plugin registration
    , Cryostat will reuse the same registration and issue a refreshed token. If
    a request contains only one of the `id`/`token` and not both, then it will
    be treated as an attempt to register a new plugin instance with the same
    reused `realm` name.

    Cryostat will perform a `POST` to the supplied `callback` when
    Cryostat restarts to ensure that the previously-registered discovery plugins
    are still present. This `POST` will have an empty body. It is recommended
    that plugins respond to this callback `POST` by attempting to re-register
    themselves by sending a request to this endpoint containing a repeated
    `realm` and `callback`, and the previously held `id` and `token`. Cryostat
    will then issue a refreshed `token` for the same `id`. Cryostat may issue a
    `callback` request to plugins at any time, in particular before token
    expiration to ensure plugins have fresh tokens. Cryostat will also send a
    `GET` request to the same `callback` at plugin registration time in order to
    validate the URI. The plugin should respond to this `GET` with a `2xx`
    response, but is not expected to attempt re-registration (since initial
    registration is still in progress).

    This initial registration request requires the plugin to provide a request
    `Authorization` header and pass authz checks. The authz here will be
    inherited by the provided access token. The access token will be scoped to
    the `id` and `realm` only.

    ###### request
    `POST /api/v2.2/discovery`
    ```json
    {
        "realm": "my-plugin",
        "callback": "http://my-plugin.my-namespace.svc.local:1234/callback"
    }
    ```

    ###### response
    `200` - The result is a JSON object containing metadata, a plugin
    registration ID, and an access token.
    ```json
   {
    "data": {
        "result": {
            "id": "922dd4f4-9d7c-4ae2-8982-0903868226a6",
            "token": "eyJjdHkiOiJKV1QiLCJlbmMiOiJBMjU2R0NNIiwiYWxnIjoiZGlyIn0..y_hecOmStA-oEtRL.ErjTrEYFRscWtTlzsBCYWu24QRnu9zs8gkSfri64pepvQzK6oxv2DMXgdtUT17M8S4a_8xmBMBzDMiPOU7sFI3tQFgskuWhjp1RC4uDxePv40A1NAi0p2Ncej0DXZ-k8s563luBhViuJXfx4y-b7eC0gmOEal852mLYbRv5Qtfi9BhS-6hqgH3XJFsoLUr6F2SG8ahFq_pF8uT1nrUnW5pmmMRE8BcnUMTKl2dbXA9Uhq-rkcTYdIUOdujJixDBgJtBODS-_1_6UbL98xBbYW5-DwtxWAeDNDmoOLMDbJrcMKyKBdvvOQ44b0adB10knIeWjlbDZ0eEuw7tjq1IPjutXz_fnBmEirWNhjh_uB-N5gllVgxn0gAm8tbU7Ed6HmN5utYg3wucPCZ2jGgr1dnujt0Kl-sgb0tARYZNYKO4JDKfZ5cI9IEck4ep_o5uE8Bv3GEtiTSZ3QVdTk-6OyPNdQfV4oQJzPDwPL2zFwBLTEuhCcCZqXcA4asdC6L7pEUSaeTruEOBG7idQ8Gb87C7ImDWBMn3PxLdXiSqAZejzDBOV61bcjMcVs-2duy_r-s2BpPGDFQ.eb4UZ82Hro0LOpkmPbSjAQ"
        }
    },
    "meta": {
        "status": "Created",
        "type": "application/json"
    }
} ```

    `401` - The user does not have sufficient permissions.

* #### `DiscoveryPostHandler`

    ###### synopsis
    Registered discovery plugins `POST` the subtrees describing their scenario
    to this endpoint. The subtree root `REALM` node is generated by Cryostat, so
    in fact the plugins publish here the *children* and descendants of that
    `REALM`, only. This is a push-based publishing system - plugins are
    responsible for performing their own monitoring of their scenario,
    transforming this into a set of leaf nodes or a tree structure, and
    `POST`ing this to Cryostat. Each request wholly replaces the previous
    `REALM` contents previously provided. The `POST` body must be in JSON form.
    The generated access token must be supplied as a query parameter, and the
    `Authorization` header is not used.

    ###### request
    `POST /api/v2.2/discovery/:id?token=:token`

    `id` - the plugin registration `id` as provided by the discovery
    registration handler.

    `token` - the access token as provided by the discovery registration
    handler.

    ```json
    [
        {
            "labels": {},
            "name": "service:jmx:rmi:///jndi/rmi://myapp.svc.local:9091/jmxrmi",
            "nodeType": "JVM",
            "target": {
                "alias": "com.MyApp",
                "annotations": {
                    "cryostat": {},
                    "platform": {}
                },
                "connectUrl": "service:jmx:rmi:///jndi/rmi://myapp.svc.local:9091/jmxrmi",
                "labels": {}
            }
        }
    ]
    ```

    ###### response

    ```json
    {
        "data": {
            "result": null
        },
        "meta": {
            "mimeType": "JSON",
            "status": "OK"
        }
    }
    ```

    `200` - The result is an empty message in JSON format.

    `400` - The JSON document provided was invalid or the provided `id` was not
    a valid format.

    `401` - The provided token did not pass authz. This may be because the token
    has expired. The plugin should re-register with the same token to receive a
    refreshed token.

    `404` - The plugin `id` could not be found. This likely occurs because the
    plugin failed a `callback` check and was pruned. The plugin should
    re-register.

* #### `DiscoveryDeregistrationHandler`

    ###### synopsis
    When plugins are shutting down they should send a request to this endpoint
    to clean up. This will immediately remove the plugin's `REALM` subtree from
    the overall discovery scenario and release its registration.

    ###### request
    `DELETE /api/v2.2/discovery/:id?token=:token`

    `id` - the plugin registration `id` as provided by the discovery
    registration handler.

    `token` - the access token as provided by the discovery registration
    handler.

    ###### response
    ```json
    {
        "data": {
            "result": "bcc0f3a6-dc48-402e-a3d6-9fbb63beff78"
        },
        "meta": {
            "mimeType": "JSON",
            "status": "OK"
        }
    }
    ```

    `200` - The result is an empty message in JSON format containing the `id`
    that was deregistered.

    `400` - The provided ID was not a valid format.

    `401` - The provided token did not pass authz. This may be because the token
    has expired. The plugin should re-register with the same token to receive a
    refreshed token.

    `404` - The plugin `id` could not be found. This likely occurs because the
    plugin failed a `callback` check and was pruned. The plugin has no need to
    deregister itself in this case.

### Events and Event Templates

* #### `TargetTemplateGetHandler`

    ###### synopsis
    Returns an event template from a target JVM, using a previously-generated
    JWT for authz rather than the Authorization header.

    ###### request
    `GET /api/v2.1/targets/:targetId/templates/:templateName/type/:templateType?token=:jwt`

    `targetId` - The location of the target JVM to connect to,
    in the form of a `service:rmi:jmx://` JMX Service URL, or `hostname:port`.
    Should use percent-encoding.

    `templateName` - The name of the template to get.

    `templateType` - The type of the template to get.

    `jwt` - The JSON Web Token providing authorization for this request. See
    [`AuthTokenPostHandler`](#AuthTokenPostHandler).

    ###### response
    `200` - The body is the requested event template, as an XML document.

    `401` - Token authentication failed. The body is an error message.

    `404` - The target or the template or the template type could not be found.
    The body is an error message.

    `427` - JMX authentication failed. The body is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `502` - JMX connection failed. This is generally because the target
    application has SSL enabled over JMX, but Cryostat does not trust the
    certificate.

    ###### example
    ```
    $ curl https://localhost:8181/api/v2.1/targets/service%3Ajmx%3Armi%3A%2F%2F%2Fjndi%2Frmi%3A%2F%2Fcryostat%3A9094%2Fjmxrmi/templates/Continuous/type/TARGET?token=(trimmed) --output Continuous.jfc
      % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                     Dload  Upload   Total   Spent    Left  Speed
    100 30133  100 30133    0     0   239k      0 --:--:-- --:--:-- --:--:--  239k
    ```


### Recordings in Target JVMs

* #### `TargetEventsGetHandler`

    ###### synopsis
    Returns a list of event types that can be produced by a target JVM,
    where the event name, category, label, etc. matches the given query.

    ###### request
    `GET /api/v2/targets/:targetId/events[?q=searchQuery]`

    `targetId` - The location of the target JVM to connect to,
    in the form of a `service:rmi:jmx://` JMX Service URL, or `hostname:port`.
    Should use percent-encoding.

    `q` - The search query. Event names, IDs, categories, and descriptions will
    be searched for case-insensitive substring matches of the supplied query. If
    this parameter is omitted or blank then all events will be returned.

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

    `502` - JMX connection failed. This is generally because the target
    application has SSL enabled over JMX, but Cryostat does not trust the
    certificate.

    ###### example
    ```
    $ curl localhost:8181/api/v2/targets/localhost/events?q=javaerrorthrow
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

    `502` - JMX connection failed. This is generally because the target
    application has SSL enabled over JMX, but Cryostat does not trust the
    certificate.

    ###### example
    ```
    $ curl localhost:8181/api/v2/targets/localhost/recordingOptionsList
    {"meta":{"status":"OK","type":"application/json"},"data":{result:[{"name":"Name","description":"Recording name","defaultValue":"Recording"},{"name":"Duration","description":"Duration of recording","defaultValue":"30s[s]"},{"name":"Max Size","description":"Maximum size of recording","defaultValue":"0B[B]"},{"name":"Max Age","description":"Maximum age of the events in the recording","defaultValue":"0s[s]"},{"name":"To disk","description":"Record to disk","defaultValue":"false"},{"name":"Dump on Exit","description":"Dump recording data to disk on JVM exit","defaultValue":"false"}]}}
    ```

* #### `TargetRecordingGetHandler`

    ###### synopsis
    Returns a recording of a target JVM as an octet stream, using a JWT for
    authorization. See [`AuthTokenPostHandler`](#AuthTokenPostHandler).

    ###### request
    `GET /api/v2.1/targets/:targetId/recordings/:recordingName?token=:jwt`

    `targetId` - The location of the target JVM to connect to,
    in the form of a `service:rmi:jmx://` JMX Service URL, or `hostname:port`.
    Should use percent-encoding.

    `recordingName` - The name of the recording to get.
    Should use percent-encoding.

    `jwt` - The JSON Web Token providing authorization for this request. See
    [`AuthTokenPostHandler`](#AuthTokenPostHandler).

    ###### response
    `200` - The body is an octet stream consisting of the requested recording.

    `401` - User authentication failed. The body is an error message.

    `404` - The target or the recording could not be found.
    The body is an error message.

    `427` - JMX authentication failed. The body is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `502` - JMX connection failed. This is generally because the target
    application has SSL enabled over JMX, but Cryostat does not trust the
    certificate.

    ###### example
    ```
    $ curl localhost:8181/api/v2.1/targets/localhost/recordings/foo?token=(trimmed) --output foo.jfr
      % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                     Dload  Upload   Total   Spent    Left  Speed
    100  530k    0  530k    0     0  9303k      0 --:--:-- --:--:-- --:--:-- 9303k
    ```

* #### `TargetReportGetHandler`

    ###### synopsis
    Returns a report of a recording of a target JVM.

    ###### request
    `GET /api/v2.1/targets/:targetId/reports/:recordingName?token=:jwt`

    `targetId` - The location of the target JVM to connect to,
    in the form of a `service:rmi:jmx://` JMX Service URL, or `hostname:port`.
    Should use percent-encoding.

    `recordingName` - The name of the recording to get the report for.
    Should use percent-encoding.

    `jwt` - The JSON Web Token providing authorization for this request. See
    [`AuthTokenPostHandler`](#AuthTokenPostHandler).

    ###### response
    `200` - The body is the requested report as an HTML document.

    `401` - User authentication failed. The body is an error message.

    `404` - The report could not be found, or the target could not be found.
    The body is an error message.

    `427` - JMX authentication failed. The body is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `502` - JMX connection failed. This is generally because the target
    application has SSL enabled over JMX, but Cryostat does not trust the
    certificate.

    ###### example
    ```
    $ curl localhost:8181/api/v2.1/targets/localhost/reports/foo?token=(trimmed) --output report.html
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
    `POST /api/v2/targets/:targetId/snapshot`

    `targetId` - The location of the target JVM to connect to,
    in the form of a `service:rmi:jmx://` JMX Service URL, or `hostname:port`.
    Should use percent-encoding.

    ###### response
    `201` - The response is a descriptor of the newly created recording, in the form
    `{"downloadUrl":"$DOWNLOAD_URL","reportUrl":"$REPORT_URL","id":$ID,"name":"$NAME","state":"$STATE","startTime":$START_TIME,"duration":$DURATION,"continuous":$CONTINUOUS,"toDisk":$TO_DISK,"maxSize":$MAX_SIZE,"maxAge":$MAX_AGE}`. The `Location` header will also be set
    to the same URL as in the `downloadUrl` field.

    `202` - The request was accepted but the recording failed to create
    because the resultant snapshot was unreadable. This could be due
    to a lack of active recordings to take event data from.

    `401` - User authentication failed. The reason is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - The target could not be found. The reason is an error message.

    `427` - JMX authentication failed. The reason is an error message.
    There will be an `X-JMX-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `502` - JMX connection failed. This is generally because the target
    application has SSL enabled over JMX, but Cryostat does not trust the
    certificate.

    ###### example
    ```
    $ curl -X POST localhost:8181/api/v2/targets/localhost/snapshot
    {"meta":{"status":"Created","type":"application/json"},"data":{"result":{"downloadUrl":"http://192.168.0.109:8181/api/v1/targets/service:jmx:rmi:%2F%2F%2Fjndi%2Frmi:%2F%2Flocalhost:9091%2Fjmxrmi/recordings/snapshot-1","reportUrl":"http://192.168.0.109:8181/api/v1/targets/service:jmx:rmi:%2F%2F%2Fjndi%2Frmi:%2F%2Flocalhost:9091%2Fjmxrmi/reports/snapshot-1","id":1,"name":"snapshot-1","state":"STOPPED","startTime":1601998841300,"duration":0,"continuous":true,"toDisk":true,"maxSize":0,"maxAge":0}}}
    ```

### Recordings in Archive

* #### `RecordingGetHandler`

    ###### synopsis
    Returns a recording that was saved to archive as an octet stream, using a
    JSON Web Token for authorization.
    **DEPRECATED**: Endpoints treating the archived recording storage as
    uncategorized storage, where files are not associated with a particular
    target application, are deprecated and will be removed in a future release.
    See [`RecordingGetWithJwtHandler`](#RecordingGetWithJwtHandler).

    ###### request
    `GET /api/v2.1/recordings/:recordingName?token=:jwt`

    `recordingName` - The name of the saved recording to get.
    Should use percent-encoding.

    `jwt` - The JSON Web Token providing authorization for this request. See
    [`AuthTokenPostHandler`](#AuthTokenPostHandler).

    ###### response
    `200` - The body is an octet stream consisting of the requested recording.

    `401` - User authentication failed. The body is an error message.

    `404` - The recording could not be found. The body is an error message.

    ###### example
    ```
    $ curl localhost:8181/api/v2.1/recordings/localhost_foo_20200910T214559Z.jfr?token=(trimmed) --output foo.jfr
      % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                     Dload  Upload   Total   Spent    Left  Speed
    100  942k  100  942k    0     0  61.3M      0 --:--:-- --:--:-- --:--:-- 61.3M
    ```

* #### `ReportGetHandler`

    ###### synopsis
    Returns the report of a recording that was saved to archive.
    **DEPRECATED**: Endpoints treating the archived recording storage as
    uncategorized storage, where files are not associated with a particular
    target application, are deprecated and will be removed in a future release.
    See [`ReportGetWithJwtHandler`](#ReportGetWithJwtHandler).

    ###### request
    `GET /api/v2.1/reports/:recordingName?token=:jwt`

    `recordingName` - The name of the recording to get the report for.
    Should use percent-encoding.

    `jwt` - The JSON Web Token providing authorization for this request. See
    [`AuthTokenPostHandler`](#AuthTokenPostHandler).

    ###### response
    `200` - The body is the requested report as an HTML document.

    `401` - User authentication failed. The body is an error message.

    `404` - The report could not be found. The body is an error message.

    ###### example
    ```
    $ curl localhost:8181/api/v2.1/reports/localhost_foo_20200911T144545Z.jfr?token=(trimmed) --output report.html
      % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                     Dload  Upload   Total   Spent    Left  Speed
    100  116k  100  116k    0     0   134k      0 --:--:-- --:--:-- --:--:--  134k
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

    `"name"`: the name of this rule definition. This must be unique, except in
    the case of "archiver rules" (see `eventSpecifier` below). This name will
    also be used to generate the name of the associated recordings.

    `"matchExpression"`: a string expression used to determine which target JVMs
    this rule will apply to. The expression has a variable named `target` in
    scope, which is of type
    [`ServiceRef`](src/main/java/io/cryostat/platform/ServiceRef.java).
    Properties can be accessed using `.` separators, and the operators `==`,
    `!=`, `||`, and `&&` are accepted, with their usual meanings. An example of
    such an expression is:
    `(target.alias == 'io.cryostat.Cryostat' || target.annotations.cryostat.JAVA_MAIN == 'io.cryostat.Cryostat') && target.annotations.cryostat.PORT != 9091`.
    Regular expressions may be used to select target properties
    matching the regular expression. This example expression will apply a rule to all targets
    whose `target.alias` starts with `abc`:
    `/^abc.*$/.test(target.alias)`.
    The simple expression `true` may also be used to create a rule which applies
    to any and all discovered targets.

    Note: The `matchExpression` format
    `/regularExpressionLiteral/.test("stringValue")` is the only regex format
    supported at the time of writing and is subject to change in the future.

    `"eventSpecifier"`: a string of the form `template=Foo,type=TYPE`, which
    defines the event template that will be used for creating new recordings in
    matching targets; or, the special string `"archive"`, which signifies that
    this rule should cause all matching targets to have their current (at the
    time of rule creation) JFR data copied to the Cryostat archives as a
    one-time operation. When using `"archive"`, it is invalid to provide
    `archivalPeriodSeconds`, `preservedArchives`, `maxSizeBytes`, or
    `maxAgeSeconds`. Such "archiver rules" are only processed once and are not
    persisted, so the `name` and `description` become optional.

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
    `DELETE /api/v2/rules/:name[?clean=true]`

    `name` - the name of the rule definition to delete.

    `clean` - optional. If set to "true", all active recordings started by this
    rule in existing target JVMs will be stopped after the rule is deleted.
    Archived copies of recordings will not be deleted. If not set to "true", all
    active recordings will remain running.

    ##### response
    `200` - The result is empty. The rule was successfully deleted.

    `401` - User authentication failed. The reason is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - No rule with the given name exists.

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

    ##### example
    ```
    $ curl http://0.0.0.0:8181/api/v2/rules
    {"meta":{"type":"application/json","status":"OK"},"data":{"result":[{"name":"Test_Rule","description":"This is a rule for testing","matchExpression":"target.alias=='io.cryostat.Cryostat'","eventSpecifier":"template=Continuous,type=TARGET","archivalPeriodSeconds":30,"preservedArchives":1,"maxAgeSeconds":30,"maxSizeBytes":-1}]}}    ```

### Stored Target Credentials

* #### `TargetCredentialsPostHandler`

    ##### synopsis
    Creates stored credentials for a given target. When an API request is made
    that requires Cryostat to connect to a JVM target with JMX authentication
    enabled, the credentials stored using this endpoint will be used.

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

    ##### example
    ```
    $ curl -X DELETE http://0.0.0.0:8181/api/v2/targets/localhost/credentials
    {"meta":{"type":"text/plain","status":"OK"},"data":{"result":null}}
    ```
* #### `TargetCredentialsGetHandler`

    ##### synopsis
    Lists targets with stored credentials.

    ##### request
    `GET /api/v2.1/credentials`

    ##### response
    `200` - The result is a list of target objects.

    `401` - User authentication failed. The reason is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    ##### example
    ```
    $ curl -X GET http://0.0.0.0:8181/api/v2.1/credentials
    {"meta":{"type":"application/json","status":"OK"},"data":{"result":[{"connectUrl":"service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi","alias":"io.cryostat.Cryostat","labels":{},"annotations":{"platform":{},"cryostat":{"HOST":"cryostat","PORT":"9091","JAVA_MAIN":"io.cryostat.Cryostat"}}},{"connectUrl":"service:jmx:rmi:///jndi/rmi://cryostat:9094/jmxrmi","alias":"es.andrewazor.demo.Main","labels":{},"annotations":{"platform":{},"cryostat":{"HOST":"cryostat","PORT":"9094","JAVA_MAIN":"es.andrewazor.demo.Main"}}}]}}
    ```

* #### `CredentialsPostHandler`

    ##### synopsis
    Creates stored credentials for a target or targets. When an API request is
    made that requires Cryostat to connect to a JVM target with JMX
    authentication enabled, the credentials stored using this endpoint will be
    used.

    ##### request
    `POST /api/v2.2/credentials`

    The request should be an HTTP form with the attributes `"matchExpression"`,
    `"username"`, and `"password"`. All are required. For more details on
    `matchExpression`, see [`RulesPostHandler`](#RulesPostHandler).

    ##### response
    `201` - The result is null. The request was processed successfully and the
    credentials were stored.

    `400` - `"username"` or `"password"` were not provided, or
    `"matchExpression"` was not provided or was invalid

    `401` - User authentication failed. The reason is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    ##### example
    ```
    $ curl -F matchExpression="target.alias == \"myservice\"" -F username=myuser -F password=mypassword http://0.0.0.0:8181/api/v2.2/credentials
    {"meta":{"type":"text/plain","status":"Created"},"data":{"result":null}}
    ```

* #### `CredentialsGetHandler`

    ##### synopsis
    List stored credentials. Only the `id`, `matchExpression` and
    `numMatchingTargets` are provided here, where `numMatchingTargets` is
    is the number of known targets matching the `matchExpression`.

    ##### request
    `GET /api/v2.2/credentials`

    ##### response
    `200` - The result is a list of stored credentials.

    `401` - User authentication failed. The reason is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    ##### example
    ```
    $ curl http://0.0.0.0:8181/api/v2.2/credentials
    {"meta":{"type":"application/json","status":"OK"},"data":{"result":[{"id":1,"matchExpression":"target.alias == \"myservice\""}]}}
    ```

* #### `CredentialGetHandler`

    ##### synopsis
    Get stored credentials. Includes the `matchExpression` and the
    list of known targets matching the `matchExpression`.

    ##### request
    `GET /api/v2.2/credentials/:id`

    `id` - the numeric ID of the credential, as listed by
    `GET /api/v2.2/credentials`

    ##### response
    `200` - The result is a credentials object.

    `401` - User authentication failed. The reason is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - No stored credentials found for the provided `id`.

    ##### example
    ```
    $ curl http://0.0.0.0:8181/api/v2.2/credentials/1
    {"meta":{"type":"application/json","status":"OK"},"data":{"result":{"matchExpression":"target.alias == \"myservice\"","targets":[]}}}
    ```

* #### `CredentialDeleteHandler`

    ##### synopsis
    Delete stored credentials.

    ##### request
    `DELETE /api/v2.2/credentials/:id`

    `id` - the numeric ID of the credential, as listed by
    `GET /api/v2.2/credentials`

    ##### response
    `200` - The result is null. The request was processed successfully and the
    credentials were deleted.

    `401` - User authentication failed. The reason is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - No stored credentials found for the provided `id`.

    ##### example
    ```
    $ curl -X DELETE http://0.0.0.0:8181/api/v2.2/credentials/1
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

    ###### example
    ```
    $ curl -F cert=@vertx-fib-demo.cer localhost:8181/api/v2/certificates
    {"meta":{"type":"text/plain","status":"OK"},"data":{"result":"/truststore/vertx-fib-demo.cer"}}
    ```

## Beta API

### Quick Reference

| What you want to do                                                       | Which handler you should use                                                            |
| ------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------|
| **Miscellaneous**                                                         |                                                                                         |
| Get the unique jvmId for a target JVM                                     | [`JvmIdGetHandler`](#JvmIdGetHandler)                                                   |
| **Recordings in Target JVMs**                                             |                                                                                         |
| Create metadata labels for a recording in a target JVM                    | [`TargetRecordingMetadataLabelsPostHandler`](#TargetRecordingMetadataLabelsPostHandler) |
| **Recordings in archive**                                                 |                                                                                         |
| Delete a recording from archive                                           | [`RecordingDeleteHandler`](#RecordingDeleteHandler-1)                                   |
| Download a recording in archive                                           | [`RecordingGetHandler`](#RecordingGetHandler-2)                                         |
| Download a recording in archive using JWT                                 | [`RecordingGetWithJwtHandler`](#RecordingGetWithJwtHandler)                             |
| Download a report of a recording in archive                               | [`ReportGetHandler`](#ReportGetHandler-3)                                               |
| Download a report of a recording in archive using JWT                     | [`ReportGetWithJwtHandler`](#ReportGetWithJwtHandler)                                   |
| Create metadata labels for a recording                                    | [`RecordingMetadataLabelsPostHandler`](#RecordingMetadataLabelsPostHandler)             |
| Upload a recording from archive to the Grafana datasource                 | [`RecordingUploadPostHandler`](#RecordingUploadPostHandler-1)                           |

### Miscellaneous
### Recordings in Target JVMs
* #### `TargetRecordingMetadataLabelsPostHandler`

    ##### synopsis
    Add metadata labels for a recording in a target JVM. Overwrites any existing labels for that recording.

    ##### request
    `POST /api/beta/targets/:targetId/recordings/:recordingName/metadata/labels`

    The request should be a JSON document with the `labels` specified as `"key": "value"` string pairs. Keys must be unique. Letters, numbers, `-`, and `.` are accepted.

    `recordingName` - The name of the recording to attach labels to.

    `targetId` - The location of the target JVM to connect to,
    in the form of a `service:rmi:jmx://` JMX Service URL, or `hostname:port`.
    Should use percent-encoding.

    ##### response
    `200` - The result contains the updated labels associated with the target recording.

    `400` - An argument was invalid. The body is an error message.

    `401` - User authentication failed. The reason is an error message. There
    will be an `X-WWW-Authenticate: $SCHEME` header that indicates the
    authentication scheme that is used.

    `404` - The recording could not be found. The body is an error message.

    ##### example
    ```
    $ curl --data "{\"myKey\":\"myValue\",\"another-key\":\"another-value\"}" http://localhost:8181/api/beta/targets/localhost:0/recordings/myRecording/metadata/labels
    {"meta":{"type":"application/json","status":"OK"},"data":{"result":{"myKey":"myValue","another-key":"another-value"}}}
    ```

### Recordings in Archives

* #### `RecordingDeleteHandler`

    ##### synopsis
    Delete a recording from archive. This does not affect any recordings in any target JVM's JFR buffer.

    ##### request
    `DELETE /api/beta/recordings/:sourceTarget/:recordingName`

    `sourceTarget` - The target JVM from which Cryostat saved the recording. Must be in the form of a service:rmi:jmx:// JMX Service URL and should use percent-encoding. If a recording was re-uploaded to archives, this field should be set to `uploads`.
    `recordingName` - The name of the recording to delete. Should use percent-encoding.

    ##### response
    `200` - The result is null. The request was processed successfully and the
    recording was deleted.

    `401` - User authentication failed. The reason is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - `recordingName` could not be found for the given `sourceTarget` or `sourceTarget` is invalid. The body is an error message.

    ##### example
    ```
    $ curl -X DELETE http://localhost:8181/api/beta/recordings/service%3Ajmx%3Armi%3A%2F%2F%2Fjndi%2Frmi%3A%2F%2Fcryostat%3A9091%2Fjmxrmi/localhost_foo_20200910T214559Z.jfr
    {"meta":{"type":"text/plain","status":"OK"},"data":{"result":null}}
    ```


* #### `RecordingGetHandler`

    ##### synopsis
    Returns a recording that was saved to archive, as an octet stream

    ##### request
    `GET /api/beta/recordings/:sourceTarget/:recordingName`

    `sourceTarget` - The target JVM from which Cryostat saved the recording. Must be in the form of a service:rmi:jmx:// JMX Service URL and should use percent-encoding. If a recording was re-uploaded to archives, this field should be set to `uploads`.
    `recordingName` - The name of the recording to download. Should use percent-encoding.

    ##### response
    `200` - The result is the recording file.

    `401` - User authentication failed. The reason is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - `recordingName` could not be found for the given `sourceTarget` or `sourceTarget` is invalid. The body is an error message.

    ##### example
    ```
    $ curl http://localhost:8181/api/beta/recordings/service%3Ajmx%3Armi%3A%2F%2F%2Fjndi%2Frmi%3A%2F%2Fcryostat%3A9091%2Fjmxrmi/localhost_foo_20200910T214559Z.jfr --output foo.jfr
    % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                    Dload  Upload   Total   Spent    Left  Speed
    100  391k  100  391k    0     0  64.7M      0 --:--:-- --:--:-- --:--:-- 76.5M
    ```

* #### `RecordingGetWithJwtHandler`

    ##### synopsis
    Returns a recording that was saved to archive, as an octet stream with JWT auth.

    ##### request
    `GET /api/beta/recordings/:sourceTarget/:recordingName/jwt`

    `sourceTarget` - The target JVM from which Cryostat saved the recording. Must be in the form of a service:rmi:jmx:// JMX Service URL and should use percent-encoding. If a recording was re-uploaded to archives, this field should be set to `uploads`.
    `recordingName` - The name of the recording to download. Should use percent-encoding.
    `jwt` - The JSON Web Token providing authorization for this request. See [`AuthTokenPostHandler`](#AuthTokenPostHandler)

    ##### response
    `200` - The result is the recording file.

    `401` - User authentication failed. The reason is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - `recordingName` could not be found for the given `sourceTarget` or `sourceTarget` is invalid. The body is an error message.

    ##### example
    ```
    $ curl http://localhost:8181/api/beta/recordings/service%3Ajmx%3Armi%3A%2F%2F%2Fjndi%2Frmi%3A%2F%2Fcryostat%3A9091%2Fjmxrmi/localhost_foo_20200910T214559Z.jfr?token=(trimmed) --output foo.jfr
    % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                    Dload  Upload   Total   Spent    Left  Speed
    100  391k  100  391k    0     0  64.7M      0 --:--:-- --:--:-- --:--:-- 76.5M
    ```

* #### `ReportGetHandler`

    ##### synopsis
    Returns the report of a recording that was saved to archive.

    ##### request
    `GET /api/beta/reports/:sourceTarget/:recordingName`

    `sourceTarget` - The target JVM from which Cryostat saved the recording. Must be in the form of a service:rmi:jmx:// JMX Service URL and should use percent-encoding. If a recording was re-uploaded to archives, this field should be set to `uploads`.
    `recordingName` - The name of the recording to get the report for. Should use percent-encoding.

    ##### response
    `200` - The body is the requested report as an HTML document.

    `401` - User authentication failed. The reason is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - `recordingName` could not be found for the given `sourceTarget` or `sourceTarget` is invalid. The body is an error message.

    ##### example
    ```
    $ curl localhost:8181/api/beta/reports/service%3Ajmx%3Armi%3A%2F%2F%2Fjndi%2Frmi%3A%2F%2Fcryostat%3A9091%2Fjmxrmi/localhost_foo_20200911T144545Z.jfr? --output report.html
    % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                    Dload  Upload   Total   Spent    Left  Speed
    100  116k  100  116k    0     0   134k      0 --:--:-- --:--:-- --:--:--  134k
    ```

* #### `ReportGetWithJwtHandler`

    ##### synopsis
    Returns the report of a recording that was saved to archive with JWT auth.

    ##### request
    `GET /api/beta/reports/:sourceTarget/:recordingName/jwt`

    `sourceTarget` - The target JVM from which Cryostat saved the recording. Must be in the form of a service:rmi:jmx:// JMX Service URL and should use percent-encoding. If a recording was re-uploaded to archives, this field should be set to `uploads`.
    `recordingName` - The name of the recording to get the report for. Should use percent-encoding.
    `jwt` - The JSON Web Token providing authorization for this request. See [`AuthTokenPostHandler`](#AuthTokenPostHandler)
    ##### response
    `200` - The body is the requested report as an HTML document.

    `401` - User authentication failed. The reason is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - `recordingName` could not be found for the given `sourceTarget` or `sourceTarget` is invalid. The body is an error message.

    ##### example
    ```
    $ curl localhost:8181/api/beta/reports/service%3Ajmx%3Armi%3A%2F%2F%2Fjndi%2Frmi%3A%2F%2Fcryostat%3A9091%2Fjmxrmi/localhost_foo_20200911T144545Z.jfr?token=(trimmed) --output report.html
    % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                    Dload  Upload   Total   Spent    Left  Speed
    100  116k  100  116k    0     0   134k      0 --:--:-- --:--:-- --:--:--  134k
    ```
* #### `RecordingUploadPostHandler`

    ##### synopsis
    Uploads a recording that was saved to archive to the Grafana datasource that Cryostat is configured with (determined by the environment variable `GRAFANA_DATASOURCE_URL`).

    ##### request
    `POST /api/beta/recordings/:sourceTarget/:recordingName/upload`

    `sourceTarget` - The target JVM from which Cryostat saved the recording. Must be in the form of a service:rmi:jmx:// JMX Service URL and should use percent-encoding. If a recording was re-uploaded to archives, this field should be set to `uploads`.
    `recordingName` - The name of the saved recording to upload. Should use percent-encoding.

    ##### response
    `200` - The body is the body of the response that Cryostat got
    after sending the upload request to the Grafana datasource server.

    `401` - User authentication failed. The body is an error message.
    There will be an `X-WWW-Authenticate: $SCHEME` header that indicates
    the authentication scheme that is used.

    `404` - `recordingName` could not be found for the given `sourceTarget` or `sourceTarget` is invalid. The body is an error message.

    `501` - The Grafana datasource URL is malformed.
    The body is an error message.

    `502` - JMX connection failed. This is generally because the target
    application has SSL enabled over JMX, but Cryostat does not trust the
    certificate.

    `512` - Cryostat received an invalid response from the
    Grafana datasource after sending the upload request.
    The body is an error message.

    ##### example
    ```
    $ curl -X POST localhost:8181/api/beta/recordings/service%3Ajmx%3Armi%3A%2F%2F%2Fjndi%2Frmi%3A%2F%2Fcryostat%3A9091%2Fjmxrmi/localhost_foo_20200911T144545Z.jfr/upload
    Uploaded: file-uploads/555f4dab-240b-486b-b336-2d0e5f43e7cd
    Loaded: file-uploads/555f4dab-240b-486b-b336-2d0e5f43e7cd
    ```
* #### `RecordingMetadataLabelsPostHandler`

    ##### synopsis
    Create metadata labels for a recording in Cryostat's archives. Overwrites any existing labels for that recording.

    ##### request
    `POST /api/beta/recordings/:sourceTarget/:recordingName/metadata/labels`

    The request should be a JSON document with the labels specified as `"key": "value"` string pairs. Keys must be unique. Letters, numbers, `-`, and `.` are accepted.

    `sourceTarget` - The target JVM from which Cryostat saved the recording.
    in the form of a `service:rmi:jmx://` JMX Service URL, or `hostname:port`.
    Should use percent-encoding. If a recording was re-uploaded to archives, this field should be
    set to `uploads`.

    `recordingName` - The name of the recording to attach labels to.

    ##### response
    `200` - The result contains the updated labels associated with the archived recording.

    `400` - An argument was invalid. The body is an error message.

    `401` - User authentication failed. The reason is an error message. There
    will be an `X-WWW-Authenticate: $SCHEME` header that indicates the
    authentication scheme that is used.

    `404` - The recording could not be found. The body is an error message.

    ##### example
    ```
    $ curl -v --data "{\"myKey\":\"updatedValue\",\"another-key\":\"another-updated-value\",\"new-key\":\"new-value\"}" http://localhost:8181/api/beta/recordings/localhost%3A0/localhost_myRecording_20220309T203725Z.jfr/metadata/labels
    {"meta":{"type":"application/json","status":"OK"},"data":{"result":{"myKey":"updatedValue","another-key":"another-updated-value","new-key":"new-value"}}}
    ```
