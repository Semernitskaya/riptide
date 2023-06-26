# Riptide: SOAP

[![Pipes](../docs/soap.jpg)](https://pixabay.com/photos/soap-bubble-bubble-frozen-bubble-1975227/)

[![Javadoc](https://www.javadoc.io/badge/org.zalando/riptide-soap.svg)](http://www.javadoc.io/doc/org.zalando/riptide-soap)
[![Maven Central](https://img.shields.io/maven-central/v/org.zalando/riptide-soap.svg)](https://maven-badges.herokuapp.com/maven-central/org.zalando/riptide-soap)

*Riptide: SOAP* adds [SOAP](https://en.wikipedia.org/wiki/SOAP) support to Riptide.

## Example

```java
http.post()
    .body(new PlaceOrderRequest(order))
    .call(soap(PlaceOrderResponse.class, this::onSuccess));
```

## Features

- adds the ability to send/receive SOAP requests/responses

## Dependencies

- JAXB
- Riptide Core

## Installation

Add the following dependency to your project:

```xml
<dependency>
    <groupId>org.zalando</groupId>
    <artifactId>riptide-soap</artifactId>
    <version>${riptide.version}</version>
</dependency>
```

## Configuration

```java
Http.builder()
    .baseUrl("https://example.org/ws/echoService")
    .converter(new SOAPHttpMessageConverter())
    .converter(new SOAPFaultHttpMessageConverter())
    .build();
```

## Usage

```java
http.post()
    .body(new PlaceOrderRequest(order))
    .call(soap(PlaceOrderResponse.class, this::onSuccess));
```

Please note that you can just use `Http#post()` without specifying any request URI or path since
the base URL itself is already enough to make a SOAP request.

The `soap` route is just a small helper covering the default scenario for SOAP responses:

- `200 OK` with a SOAP body
- `500 Internal Server Error` with a SOAP fault (thrown as a `SOAPFaultException`)

It's equivalent to:

```java
http.post()
    .body(new PlaceOrderRequest(order))
    .dispatch(status(),
        on(OK).call(PlaceOrderResponse,class, this::onSuccess),
        on(INTERNAL_SERVER_ERROR).call(SOAPFault.class, fault -> {
            throw new SOAPFaultException(fault);
        }))
```

In case the response is not interesting it can be discarded:

```java
http.post()
    .body(new PlaceOrderRequest(order))
    .call(soap(pass()));
```

## Getting Help

If you have questions, concerns, bug reports, etc., please file an issue in this repository's [Issue Tracker](../../../../issues).

## Getting Involved/Contributing

To contribute, simply open a pull request and add a brief description (1-2 sentences) of your addition or change. For
more details, check the [contribution guidelines](../.github/CONTRIBUTING.md).
