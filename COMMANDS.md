# Commands

## Introduction

This document provides a brief listing of the commands implemented by this
client, including a short synopsis of any expected arguments.

## Command Listing

### Core

* #### `help`
    ###### usage
    `help`
    ###### synopsis
    Lists available commands. The output may vary depending on whether the client
    has an active connection to a target JVM.

* #### `connect`
    ###### usage
    `connect foo` | `connect foo:1234` | `connect 10.130.0.4:1234`
    ###### synopsis
    Connect to a target JVM. One argument is expected, which is the hostname
    (`foo`) or address (`10.130.0.4`) of the target JVM with an optional port
    number (`1234`). The default RJMX port used if unspecified is 9091.
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
    [`hostname`](#hostname)

* #### `hostname`
    ###### usage
    `hostname`
    ###### synopsis
    Prints the Hostname of the client container.
    ###### see also
    [`ip`](#ip)

* #### `wait`
    ###### usage
    `wait 10`
    ###### synopsis
    Waits for (at least) the specified number of seconds (`10`). This cannot be
    interrupted.
    ###### see also
    * [`wait-for`](#wait-for)
    * [`wait-for-download`](#wait-for-download)

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

    The eventID is the fully qualified event name. For information about the
    events and options available, see `list-event-types` or `search-events`.
    ###### see also
    * [`list-event-types`](#list-event-types)
    * [`search-events`](#search-events)
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

* #### `recording-option`
    ###### usage
    `recording-option toDisk=true`
    ###### synopsis
    Sets the given option (`toDisk`) to the specified value (`true`). The
    currently supported options are toDisk (boolean), maxAge (seconds),
    maxSize (bytes), and destinationCompressed (boolean).

    These recording options are local to the client session, not the connected
    target JVM. In practical terms this means, for example, that setting
    `recording-option toDisk=true` will cause all subsequent `start`, `dump`,
    and `snapshot` commands to save recording contents to disk, even if the
    client is disconnected from the initial target JVM and connected to a second
    target JVM.

* #### `list`
    ###### usage
    `list`
    ###### synopsis
    Lists recordings in the target JVM. The name provided in this list is the
    name that can be used to download the recording, as well as to pass to
    other commands which operate upon recordings.

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

* #### `wait-for-download`
    ###### usage
    `wait-for-download foo`
    ###### synopsis
    Waits until the client's embedded webserver services a request to download
    the given recording (`foo`). This does _not_ imply that the recording has
    completed - a fixed-duration recording may still be in progress, and a
    non-fixed recording may be actively running. This command is mostly useful
    for non-interactive client usage, ex. shell scripting. Once this command
    has begun awaiting download of the recording it cannot be interrupted.