# test-publish-data-protos

A minimal Spring Boot (v4) web application, built with Java 24 and Gradle, that generates
random test data on the fly using the protobuf message types from
[`test-data-protos`](https://github.com/Subhajitdas298/test-data-protos) and exposes it
over a fully open (unauthenticated) REST API.

There is no persistence/data layer — data is generated in a loop inside the service layer
on every request.

## Data shape

Each request generates:

- **10 days** of data (`DataEntry.dates`, one `DateRecord` per day)
- Each day has **26 fields** (`a`–`z`, matching the proto definition)
- Each field contains **10,000 randomly generated `double` records**

That's `10 * 26 * 10,000 = 2,600,000` values per request.

## API

| Method | Path        | Description                                    |
|--------|-------------|-------------------------------------------------|
| GET    | `/api/data` | Generates and returns a fresh dataset as JSON  |

No authentication, no request parameters.

## Dependency on `test-data-protos`

This project depends on the Java package published from
[`Subhajitdas298/test-data-protos`](https://github.com/Subhajitdas298/test-data-protos) to
GitHub Packages:

```kotlin
implementation("com.github.subhajitdas298:test-data-protos:1.0.1")
```

GitHub Packages requires authentication to *resolve* Maven artifacts, even for public
repositories. Before building, export a GitHub personal access token with `read:packages`
scope:

```bash
export GITHUB_ACTOR=<your-github-username>
export GITHUB_TOKEN=<your-personal-access-token>
```

`build.gradle.kts` reads these two environment variables to authenticate against
`https://maven.pkg.github.com/Subhajitdas298/test-data-protos`.

> Note: the `test-data-protos` package is published only when a GitHub Release is cut on
> that repository. Make sure version `1.0.1` (or whatever version you point at) has
> actually been published before building this project.

## Running

Requires Java 24 (the Gradle wrapper will auto-provision it via the Foojay toolchain
resolver if it's not already installed).

```bash
./gradlew bootRun
```

Then:

```bash
curl http://localhost:8080/api/data
```

## Tech stack

- Spring Boot 4
- Java 24
- Gradle (Kotlin DSL)
- `com.github.subhajitdas298:test-data-protos` (protobuf-generated Java models)
- `protobuf-java-util` (for JSON serialization of the protobuf messages)
