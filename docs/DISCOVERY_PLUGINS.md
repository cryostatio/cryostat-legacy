# Discovery Plugins

As of Cryostat v2.2, the `/api/v2.2/discovery` and related API endpoints can be
used to register new external plugins which provide Cryostat with information
about discoverable targets known to the plugin. For more details on the API
request specifics, see [HTTP_API.md](./HTTP_API.md). This document serves as a
high-level description and explains the motivations for this API.

## Server-side API

On the server (Cryostat) side, the discovery API consists of a set of related
endpoints which allow a client (plugin) to 1. register with Cryostat, 2. publish
information about target applications, and 3. deregister from Cryostat. This is
intended to provide Cryostat end-users with an extensible interface for fitting
Cryostat into their own particular deployment scenarios. For example, when
Cryostat detects that it is running in a `k8s` environment, it tries to discover
target applications by querying `Endpoints` objects in the `Namespace` and
filtering those by `port.number == 9091 || port.name == 'jfr-jmx'`, and this
behaviour is hardcoded. This can be disabled in Cryostat v2.2+ by setting the
environment variable `CRYOSTAT_DISABLE_BUILTIN_DISCOVERY=true` if this hardcoded
behavioural assumption does not hold. End users may author a program that
bridges a service locator with the Cryostat discovery interface, or end users
may modify their own applications to self-publish themselves to the discovery
interface directly. This can be used as a supplement to or a replacement for
Cryostat's built-in discovery mechanisms.

## Client-side API

On the client (Plugin) side, the discovery API requirements are a relatively low
barrier. The client must be able to: send HTTP requests to the Cryostat server;
pass an `Authorization` header with acceptable Cryostat credentials; accept JSON
formatted responses; and publish information about discoverable target
applications in JSON format. The plugin must also be able to receive `GET` and
`POST` requests initiated by Cryostat on a single endpoint URL as specified by
the plugin itself.
