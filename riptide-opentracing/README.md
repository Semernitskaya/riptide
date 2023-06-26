# Riptide: OpenTracing

[![Spider web](../docs/spider-web.jpg)](https://pixabay.com/photos/cobweb-drip-water-mirroring-blue-3725540/)

[![Javadoc](https://www.javadoc.io/badge/org.zalando/riptide-micrometer.svg)](http://www.javadoc.io/doc/org.zalando/riptide-micrometer)
[![Maven Central](https://img.shields.io/maven-central/v/org.zalando/riptide-micrometer.svg)](https://maven-badges.herokuapp.com/maven-central/org.zalando/riptide-micrometer)
[![OpenTracing](https://img.shields.io/badge/OpenTracing-enabled-blue.svg)](http://opentracing.io)

*Riptide: OpenTracing* adds sophisticated [OpenTracing](https://opentracing.io/) support to *Riptide*.

## Example

```java
Http.builder()
    .plugin(new OpenTracingPlugin(tracer))
    .build();
```

## Features

- Client span lifecycle management
- Span context injection into HTTP headers of requests
- Extensible span decorators for tags and logs
- Seamless integration with [Riptide: Failsafe](../riptide-failsafe)

## Dependencies

- Riptide Core
- [OpenTracing Java API](https://opentracing.io/guides/java/)
- [Riptide: Failsafe](../riptide-failsafe) (optional)

## Installation

Add the following dependency to your project:

```xml
<dependency>
    <groupId>org.zalando</groupId>
    <artifactId>riptide-opentracing</artifactId>
    <version>${riptide.version}</version>
</dependency>
```

## Configuration

```java
Http.builder()
    .baseUrl("https://www.example.com")
    .plugin(new OpenTracingPlugin(tracer)
        .withLifecycle(new NewSpanLifecycle())
        .withActivation(new NoOpActivation())
        .withAdditionalSpanDecorators(new HttpUrlSpanDecorator))
    .build();
```

By default a new span will be started for each request and it will be activated.

The following tags/logs are supported out of the box:

| Tag/Log Field                | Decorator                          | Example                           |
|------------------------------|------------------------------------|-----------------------------------|
|                              | `CallSiteSpanDecorator`            | `admin=true`                      |
| `component`                  | `ComponentSpanDecorator`           | `Riptide`                         |
|                              | `CompositeSpanDecorator`³          |                                   |
| `message` (log)              | `ErrorMessageSpanDecorator`¹       | `Connection timed out`            |
| `error`                      | `ErrorSpanDecorator`               | `false`                           |
| `error.kind` (log)           | `ErrorSpanDecorator`               | `SocketTimeoutException`          |
| `error.object` (log)         | `ErrorSpanDecorator`               | (exception instance)              |
| `stack` (log)                | `ErrorStackSpanDecorator`          | `SocketTimeoutException at [...]` |
| `http.method_override`       | `HttpMethodOverrideSpanDecorator`  | `GET`                             |
| `http.method`                | `HttpMethodSpanDecorator`          | `POST`                            |
| `http.url`                   | `HttpUrlSpanDecorator`¹            | `https://www.github.com/users/me` |
| `http.path`                  | `HttpPathSpanDecorator`            | `/users/{user_id}`                |
| `http.prefer`                | `HttpPreferSpanDecorator`          | `respond-async`                   |
| `http.retry_after` (log)     | `HttpRetryAfterSpanDecorator`      | `120`                             |
| `http.status_code`           | `HttpStatusCodeSpanDecorator`      | `200`                             |
| `peer.address`               | `PeerSpanDecorator`                | `www.github.com:80`               |
| `peer.hostname`              | `PeerSpanDecorator`                | `www.github.com`                  |
| `peer.port`                  | `PeerSpanDecorator`                | `80`                              |
| `retry`                      | `RetrySpanDecorator`¹              | `true`                            |
| `retry_number` (log)         | `RetrySpanDecorator`¹              | `3`                               |
|                              | `ServiceLoaderSpanDecorator`²      |                                   |
|                              | `StaticTagSpanDecorator`¹          | `zone=aws:eu-central-1a`          |
|                              | `UriVariablesTagSpanDecorator`¹    | `user_id=me`                      |


¹ **Not** registered by default.  
² The `ServiceLoaderSpanDecorator` will load all custom `SpanDecorator` implementations that are registered using [Java's Service Provider Interface](https://docs.oracle.com/javase/tutorial/ext/basics/spi.html) mechanism and delegate to them.  
³ The `CompositeSpanDecorator` allows to treat multiple decorators as one.

### Notice

**Be aware**: The `http.url` tag is disabled by default because the full request URI may contain
sensitive, [*personal data*](https://en.wikipedia.org/wiki/General_Data_Protection_Regulation).
As an alternative we introduced the `http.path` tag which favors the URI template over the
already expanded version. That has the additional benefit of a significant lower cardinality
compared to what `http.url` would provide. 

If you still want to enable it, you can do so by just registering the missing span decorator:

```java
new OpenTracingPlugin(tracer)
    .withAdditionalSpanDecorators(new HttpUrlSpanDecorator())
```

### Lifecycle 

A lifecycle policy can be used to specify which spans are reused or whether a new one is created:

```java
new OpenTracingPlugin(tracer)
    .withLifecycle(new NewSpanLifecycle());
```

#### Active Span Lifecycle 

The `ActiveSpanLifecycle` reuses the current active span. This approach might be useful if some other
facility already provided a span that can be used to decorate with *tags*.

```java
Http http = Http.builder()
    .baseUrl("https://www.example.com")
    .plugin(new OpenTracingPlugin(tracer)
        .withLifecycle(new ActiveSpanLifecycle()))
    .build();

Span span = tracer.buildSpan("test").start();

try (final Scope ignored = tracer.activateSpan(span)) {
    http.get("/users/{user}", "me")
            .dispatch(..)
            .join();
} finally {
    span.finish();
}
```

#### Explicit Span Lifecycle 

The `ExplicitSpanLifecycle` reuses the span passed with the `OpenTracingPlugin.SPAN` attribute.
That allows to pass a span explicitly rather than implicitly via the *active span* mechanism. This might be needed
for system that can't rely on `ThreadLocal` state, e.g. non-blocking, event-loop based reactive applications.

```java
Http http = Http.builder()
    .baseUrl("https://www.example.com")
    .plugin(new OpenTracingPlugin(tracer)
        .withLifecycle(new ExplicitSpanLifecycle()))
    .build();

Span span = tracer.buildSpan("test").start();

http.get("/users/{user}", "me")
        .attribute(OpenTracingPlugin.SPAN, span)
        .dispatch(..)
        .join();
    
span.finish();
```

#### New Span Lifecycle 

The `NewSpanLifecycle` starts and finishes a new span for every request. This policy is the most common approach and therefore the default (in conjunction with the `ExplicitSpanLifecycle`).

```java
Http http = Http.builder()
    .baseUrl("https://www.example.com")
    .plugin(new OpenTracingPlugin(tracer)
        .withLifecycle(new NewSpanLifecycle()))
    .build();

http.get("/users/{user}", "me")
        .dispatch(..)
        .join();
```

#### Lifecycle composition

Different lifecycle policies can be chained together:

```java
new OpenTracingPlugin(tracer)
    .withLifecycle(Lifecycle.composite(
            new ActiveSpanLifecycle(),
            new ExplicitSpanLifecycle(),
            new NewSpanLifecycle()
    ));
```

If a policy doesn't produce a span the next one will be used and so on and so forth. Tracing will effectively be disabled if none of the policies produces a span. 

### Activation 

An activation policy can be used to specify whether a span will be activated or not. This might be desired for system that can't rely on `ThreadLocal` state, e.g. non-blocking, event-loop based reactive applications.

```java
new OpenTracingPlugin(tracer)
    .withActivation(new NoOpActivation());
```

### Span Context Injection

An injection policy can be used to specify whether (or how) the span context will be injected into outgoing requests. The default configuration enables span context propagation, but it's not always desired and can be disabled:

```java
new OpenTracingPlugin(tracer)
    .withInjection(new NoOpInjection())
```

### Span Decorators

Span decorators are a simple, yet powerful tool to manipulate the span, i.e. they allow you to add tags, logs and baggage to spans. The default set of decorators can be extended by using `OpenTracingPlugin#withAdditionalSpanDecorators(..)`:

```java
new OpenTracingPlugin(tracer)
    .withAdditionalSpanDecorators(new StaticSpanDecorator(singletonMap(
            "environment", "local"
    )))
```

If the default span decorators are not desired you can replace them completely using `OpenTracingPlugin#withSpanDecorators(..)`:

```java
new OpenTracingPlugin(tracer)
        .withSpanDecorators(
            new ComponentSpanDecorator("MSIE"),
            new PeerSpanDecorator(),
            new HttpMethodSpanDecorator(),
            new HttpPathSpanDecorator(),
            new HttpUrlSpanDecorator(),
            new HttpStatusCodeSpanDecorator(),
            new ErrorSpanDecorator(),
            new CallSiteSpanDecorator())
```

## Usage

Typically you won't need to do anything at the call-site regarding OpenTracing, i.e. your usages of Riptide should work exactly as before:

```java
http.get("/users/{id}", userId)
    .dispatch(series(),
        on(SUCCESSFUL).call(User.class, this::greet),
        anySeries().call(problemHandling()))
```

### Operation Name

By default the HTTP method will be used as the operation name, which might not fit your needs. Since deriving a meaningful operation name from request arguments alone is unreliable, you can specify the `OpenTracingPlugin.OPERATION_NAME` request attribute to override the default:

```java
http.get("/users/{id}", userId)
    .attribute(OpenTracingPlugin.OPERATION_NAME, "get_user")
    .dispatch(series(),
        on(SUCCESSFUL).call(User.class, this::greet),
        anySeries().call(problemHandling()))
```

### Call-Site Tags

Assuming you have the [`CallSiteSpanDecorator`](#span-decorators) registered (it is by default), you can also specify custom tags based on context information which wouldn't be available within the plugin anymore:

```java
http.get("/users/{id}", userId)
    .attribute(OpenTracingPlugin.TAGS, singletonMap("retry", "true"))
    .dispatch(series(),
        on(SUCCESSFUL).call(User.class, this::greet),
        anySeries().call(problemHandling()))
```

### URI Variables as Tags

URI templates are not just safer to use (see [Configuration](#notice)), they can also be used to generate tags from URI variables. Given you have the `UriVariablesTagSpanDecorator` registered then the following will produce a `user_id=123` tag:

```java
http.get("/users/{user_id}", 123)
```

The same warning applies as mentioned before regarding [`http.url`](#notice). This feature may
expose *personal data* and should be used with care.

## Getting Help

If you have questions, concerns, bug reports, etc., please file an issue in this repository's [Issue Tracker](../../../../issues).

## Getting Involved/Contributing

To contribute, simply open a pull request and add a brief description (1-2 sentences) of your addition or change. For
more details, check the [contribution guidelines](../.github/CONTRIBUTING.md).
