# test-publish-data-protos

A minimal Spring Boot (v4) web application, built with Java 24 and Gradle, that generates
random test data using the protobuf message types from
[`test-data-protos`](https://github.com/Subhajitdas298/test-data-protos) and publishes it
over a fully open (unauthenticated) REST API — as raw protobuf binary or as JSON.

There is no database — the repository layer generates the dataset in a loop, and every
layer (repository + both services) caches its output via Spring's cache abstraction
(`@Cacheable`), so the generation loop only runs once, on the first request to either
endpoint.

## Architecture

- **`DataRepository`** (repository layer) — generates the raw `Root` protobuf message in a
  loop and caches it (`rawDataset` cache).
- **`ProtoDataService`** — reads from the repository and caches the serialized protobuf
  bytes (`protoDataset` cache).
- **`JsonDataService`** — reads from the repository and caches the JSON representation
  (`jsonDataset` cache).
- **`DataController`** — exposes both services as REST endpoints.

## Data shape

The generated dataset (a `Root` protobuf message) consists of:

- **10 days** of data (`DataEntry.dates`, one `DateRecord` per day)
- Each day has **26 fields** (`a`–`z`, matching the proto definition)
- Each field contains **10,000 randomly generated `double` records**

That's `10 * 26 * 10,000 = 2,600,000` values, generated once and reused for every request.

## API

| Method | Path             | Description                                                  |
|--------|------------------|----------------------------------------------------------------|
| GET    | `/api/data`      | Returns the cached dataset as raw protobuf binary (`Root` message) |
| GET    | `/api/data/json` | Returns the same cached dataset as JSON                       |

`/api/data` responds with content type `application/x-protobuf`. The body is the serialized
bytes of the `Root` message defined in `test-data-protos` — decode it with
`Root.parseFrom(bytes)` in any consumer that has the same proto package on its classpath.

`/api/data/json` responds with content type `application/json`, using protobuf's standard
JSON mapping (via `JsonFormat`).

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
curl http://localhost:8080/api/data --output data.pb
curl http://localhost:8080/api/data/json
```

## Tech stack

- Spring Boot 4
- Java 24
- Gradle (Kotlin DSL)
- `com.github.subhajitdas298:test-data-protos` (protobuf-generated Java models)
- `protobuf-java-util` (protobuf binary serialization + `JsonFormat` for JSON)
- Spring's cache abstraction (`spring-boot-starter-cache` + `@EnableCaching` + `@Cacheable`)
  for in-memory caching at both the repository and service layers
