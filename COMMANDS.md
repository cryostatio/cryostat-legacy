# Commands

## Introduction

This document provides a brief listing of the commands implemented by this
client, including a short synopsis of any expected arguments. Sample outputs
reflect the human-friendly output produced when running `container-jfr` in
`tty` or `tcp` modes. In `ws` mode the output will be semantically similar but
formatted as a JSON response.

## Command Listing

### Core

* #### `help`
    ###### usage
    `help`
    ###### synopsis
    Lists available commands. The output may vary depending on whether the client
    has an active connection to a target JVM.

* #### `scan-targets`
    ###### usage
    `scan-targets`
    ###### synopsis
    Scans for discoverable target JVMs. This may use various discovery
    mechanisms, including Kubernetes service discovery or JDP. For more details
    see [this document](https://github.com/rh-jmc-team/container-jfr#monitoring-applications).

* #### `is-connected`
    ###### usage
    `is-connected`
    ###### synopsis
    Outputs the connection status of `container-jfr` to a target JVM. If no
    target connection exists then the output is `Disconnected`. If a connection
    does exist then the output is of the form `foo:1234`, where `foo` is the
    hostname of the target and `1234` is the connected RJMX port.

* #### `connect`
    ###### usage
    `connect foo` | `connect foo:1234` | `connect 10.130.0.4:1234`
    ###### synopsis
    Connect to a target JVM. One argument is expected, which is the hostname
    (`foo`) or address (`10.130.0.4`) of the target JVM with an optional port
    number (`1234`), or a JMX service URL. The default RJMX port used if
    unspecified is 9091.
    ###### see also
    [`disconnect`](#disconnect)

* #### `disconnect`
    ###### usage
    `disconnect`
    ###### synopsis
    Disconnect from the target JVM. Recording options (see `recording-option`)
    are local to the _client_, so these are preserved across client
    connections. Recordings are stored within the target JVM and so those are
    also preserved after a disconnection, and will still be available upon
    reconnection.
    ###### see also
    [`connect`](#connect)

* #### `ping`
    ###### usage
    `ping`
    ###### synopsis
    Used to test the connection between `container-jfr` and whatever end client
    is in use by the user, whether that is ex. netcat over a TCP socket or
    `container-jfr-web`. Outputs "pong" unconditionally, signifying the
    connection is open and working.

* #### `exit`
    ###### usage
    `exit`
    ###### synopsis
    Exit the client session. Recording option settings will be lost, but
    recordings themselves will be maintained so long as the containing targets
    stay alive.

* #### `ip`
    ###### usage
    `ip`
    ###### synopsis
    Prints the IP address of the client container.
    ###### see also
    * [`hostname`](#hostname)
    * [`url`](#url)

* #### `hostname`
    ###### usage
    `hostname`
    ###### synopsis
    Prints the Hostname of the client container.
    ###### see also
    * [`ip`](#ip)
    * [`url](#url)

* #### `url`
    ###### usage
    `url`
    ###### synopsis
    Prints the embedded webserver download URL for the client. Recordings can
    be downloaded from `$URL/$RECORDING`, where `$URL` is the output from this
    command and `$RECORDING` is the name of a recording in the target JVM.
    This information is also printed upon any successful connection to a target
    JVM.
    ###### see also
    * [`list`](#list)

* #### `wait`
    ###### usage
    `wait 10`
    ###### synopsis
    Waits for (at least) the specified number of seconds (`10`). This cannot be
    interrupted.
    ###### see also
    * [`wait-for`](#wait-for)

### Flight Recorder

* #### `start`
    ###### usage
    `start foo jdk.SocketRead:enabled=true,jdk.PhysicalMemory:period=10ms`
    ###### synopsis
    Starts a continuous recording in the target JVM with the given name
    (`foo`), which will record events as configured in the events string.

    The syntax of an individual event string is `eventID:option=value`.
    The syntax of the overall events string is `event1,event2,event3`, for
    N >= 1 events.

    The event string may also be provided in the form `template=Foo`. This
    format allows preset configurations of events and options to be enabled.

    The eventID is the fully qualified event name. For information about the
    events and options available, see `list-event-types` or `search-events`.
    ###### see also
    * [`list-event-types`](#list-event-types)
    * [`search-events`](#search-events)
    * [`list-event-templates`](#list-event-templates)
    * [`dump`](#dump)

* #### `stop`
    ###### usage
    `stop foo`
    ###### synopsis
    Stops the given recording (`foo`).
    ###### see also
    [`start`](#start)

* #### `dump`
    ###### usage
    `dump foo 30 jdk.SocketRead:enabled=true`
    ###### synopsis
    Starts a recording with the given name (`foo`) with a fixed duration of the
    given number of seconds (`30`), recording events as configured in the
    events string.
    ###### see also
    [`start`](#start)

* #### `snapshot`
    ###### usage
    `snapshot`
    ###### synopsis
    Creates a recording named `snapshot-n`, where n is a sequentially assigned
    ID, which contains information about all events recorded across all active
    recordings at the time of invocation.
    ###### see also
    * [`dump`](#dump)
    * [`start`](#start)

* #### `save`
    ###### usage
    `save foo`
    ###### synopsis
    Saves the named recording to persistent storage attached to the
    `container-jfr` container. The saved recording contains a snapshot of its
    parent in-memory recording at the time of the save and is not updated
    (unless overwritten by a new save in the future). A saved recording is not
    tied to the lifecycle of the JVM which produced it - that is, the recording
    will remain available even if the target JVM dies.

    For `container-jfr` to be able to save recordings to persistent storage,
    there must be persistent storage available to the container. The storage is
    expected to be mounted at the path `/flightrecordings` within the
    container. The exact type of persistent storage is not important, so long
    as it is a writeable directory.
    ###### see also
    * [`delete`](#delete)
    * [`snapshot`](#snapshot)

* #### `upload-recording`
    ###### usage
    `upload-recording foo`
    ###### synopsis
    Uploads the named recording to a jfr-datasource instance, which exposes the
    information contained within the recording to its associated Grafana
    instance. For information on setting environment variables to enable
    uploading, see README.md .
    ###### see also
    * [`dump`](#dump)
    * [`start`](#start)

* #### `delete`
    ###### usage
    `delete foo`
    ###### synopsis
    Deletes the named recording, removing it from the target JVM and freeing
    the buffer memory used. The recording will be stopped automatically if it
    is running at the time of deletion.
    ###### see also
    * [`stop`](#stop)
    * [`delete-saved`](#delete-saved)

* #### `delete-saved`
    ###### usage
    `delete-saved foo.jfr`
    ###### synopsis
    Deletes the named recording from persistent storage. This does not affect
    any recordings in the target JVM's JFR buffer, even if the named saved
    recording was created from and named identically to the in-memory
    recording.
    ###### see also
    * [`save`](#save)
    * [`delete`](#delete)

* #### `search-events`
    ###### usage
    `search-events foo`
    ###### synopsis
    Searches for event types that can be produced by the target JVM where the
    event name, category, label, etc. matches the given query (`foo`). This
    is useful for preparing event options strings.
    ###### see also
    * [`start`](#start)
    * [`dump`](#dump)
    * [`list-event-types`](#list-event-types)
    * [`list-event-templates`](#list-event-templates)

* #### `list-event-types`
    ###### usage
    `list-event-types`
    ###### synopsis
    Lists event types that can be produced by the target JVM.
    This is useful for preparing event options strings.
    ###### see also
    * [`start`](#start)
    * [`dump`](#dump)
    * [`search-events`](#search-events)
    * [`list-event-templates`](#list-event-templates)

* #### `list-event-templates`
    ###### usage
    `list-event-templates`
    ###### synopsis
    Lists event templates, which are configurations of event types with
    preset values for their associated options.
    These may include templates defined and supported by the currently connected
    remote target JVM as well as customized templates known to `container-jfr`.
    ###### see also
    * [`start`](#start)
    * [`dump`](#dump)
    * [`search-events`](#search-events)
    * [`list-event-types`](#list-event-types)

* #### `recording-option`
    ###### usage
    `recording-option toDisk=true` | `recording-option -toDisk`
    ###### synopsis
    Sets the given option (`toDisk`) to the specified value (`true`), or unsets
    the option and restores the target JVM default.
    The currently supported options are toDisk (boolean), maxAge (seconds),
    maxSize (bytes), destinationCompressed (boolean), and destinationFile
    (string).

    These recording options are local to the client session, not the connected
    target JVM. In practical terms this means, for example, that setting
    `recording-option toDisk=true` will cause all subsequent `start`, `dump`,
    and `snapshot` commands to save recording contents to disk, even if the
    client is disconnected from the initial target JVM and connected to a second
    target JVM.

* #### `list`
    ###### usage
    `list target`
    ###### synopsis
    Lists recordings in the specified target JVM. The name provided in this list
    is the name to pass to other commands which operate upon recordings.
    ###### see also
    [`list-saved`](#list-saved)

* #### `list-saved`
    ###### usage
    `list-saved`
    ###### synopsis
    Lists saved recordings in persistent storage attached to `container-jfr`.
    #### see also
    [`list`](#list)

* #### `list-recording-options`
    ###### usage
    `list-recording-options`
    ###### synopsis
    Lists recording options which may be set for recordings within the current
    target JVM. Not all options are guaranteed to be supported by the client.
    ###### see also
    [`recording-option`](#recording-option)

* #### `wait-for`
    ###### usage
    `wait-for foo`
    ###### synopsis
    Waits for the given recording (`foo`) to stop running. If the recording is
    continuous and not already stopped then the command will refuse to wait for
    the recording to complete, since this would lock up the client and require
    another client to be connected in order to stop the recording. Once this
    command has begun awaiting completion of the recording it cannot be
    interrupted.
