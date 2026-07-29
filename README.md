# test-publish-data-protos

A minimal Spring Boot (v4) web application, built with Java 24 and Gradle, that serves
precomputed test data using the protobuf message types from
[`test-data-protos`](https://github.com/Subhajitdas298/test-data-protos) and publishes it
over a fully open (unauthenticated) REST API — as raw protobuf binary or as JSON.

There is no database — the repository layer builds the dataset from a precomputed binary
array bundled as a resource (`src/main/resources/data/dataset.bin`), and every layer
(repository + both services) caches its output via Spring's cache abstraction
(`@Cacheable`), so the file is only read and the protobuf message only built once, on the
first request to either endpoint.

## Architecture

- **`DataRepository`** (repository layer) — reads `data/dataset.bin` (2,600,000
  precomputed `double`s, stored as big-endian 8-byte values) into a `DoubleBuffer` and
  builds the raw `Root` protobuf message from it, caching the result (`rawDataset` cache).
- **`ProtoDataService`** — reads from the repository and caches the serialized protobuf
  bytes (`protoDataset` cache).
- **`JsonDataService`** — reads from the repository and caches the JSON representation
  (`jsonDataset` cache).
- **`DataController`** — exposes both services on a single URL, differentiated purely by
  the `Accept` header (HTTP content negotiation).

## Data shape

The dataset (a `Root` protobuf message) consists of:

- **10 days** of data (`DataEntry.dates`, one `DateRecord` per day)
- Each day has **26 fields** (`a`–`z`, matching the proto definition)
- Each field contains **10,000 precomputed `double` records**, read in order from
  `data/dataset.bin`

That's `10 * 26 * 10,000 = 2,600,000` values, read from the bundled file once and reused
for every request.

## API

There is a single endpoint. The representation is chosen purely by the `Accept` header
(standard HTTP content negotiation) — there is no separate path for JSON.

| Method | Path        | `Accept` header          | Response                                              |
|--------|-------------|---------------------------|--------------------------------------------------------|
| GET    | `/api/data` | `application/x-protobuf` | Raw protobuf binary — serialized bytes of the `Root` message. Decode with `Root.parseFrom(bytes)`. |
| GET    | `/api/data` | `application/json`       | The same dataset as JSON, using protobuf's standard JSON mapping (via `JsonFormat`). |

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
curl http://localhost:8080/api/data -H "Accept: application/x-protobuf" --output data.pb
curl http://localhost:8080/api/data -H "Accept: application/json"
```

## Tech stack

- Spring Boot 4
- Java 24
- Gradle (Kotlin DSL)
- `com.github.subhajitdas298:test-data-protos` (protobuf-generated Java models)
- `protobuf-java-util` (protobuf binary serialization + `JsonFormat` for JSON)
- Spring's cache abstraction (`spring-boot-starter-cache` + `@EnableCaching` + `@Cacheable`)
  for in-memory caching at both the repository and service layers

## Performance

- **Virtual threads** (`spring.threads.virtual.enabled: true`) — the embedded Tomcat
  connector handles each request on a virtual thread instead of a bounded platform-thread
  pool, so slow clients pulling the large (tens-of-MB) response bodies can't exhaust a
  fixed pool of OS threads.
- **Response compression** (`server.compression.enabled: true`, applied to
  `application/json` and `application/x-protobuf`) — meaningfully shrinks the ~50 MB JSON
  and protobuf payloads over the wire.
- **JVM flags for a scale-to-zero container** (see [`Dockerfile`](Dockerfile)):
  `-XX:+UseSerialGC` and `-XX:MaxRAMPercentage=75.0` (lower-footprint GC using most of the
  container's memory, since the JVM is the only process in it) and
  `-XX:TieredStopAtLevel=1` (skips C2 warmup) — all aimed at cutting cold-start latency
  after the container app scales back up from zero.

## Deployment (Azure Container Apps)

[`.github/workflows/deploy.yml`](.github/workflows/deploy.yml) builds the jar, builds a
Docker image (see [`Dockerfile`](Dockerfile)), pushes it to Azure Container Registry, and
updates the Azure Container App to the new image, on every push to `main` (which includes
merging a PR into `main`) and via manual `workflow_dispatch`.

It authenticates to Azure with OIDC (`azure/login`, no client secret stored in GitHub).

### Azure resources you need to create once

These have already been provisioned for this repo (see "Provisioned resources" below);
this section is kept so the setup can be reproduced in another subscription.

```bash
SUBSCRIPTION_ID=<your-subscription-id>
RESOURCE_GROUP=data-protos
LOCATION=eastus
ACR_NAME=<globally-unique-name>          # e.g. dataprotosacr1234
ENVIRONMENT_NAME=cae-data-protos
APP_NAME=data-protos

az account set --subscription "$SUBSCRIPTION_ID"

# Resource group
az group create --name "$RESOURCE_GROUP" --location "$LOCATION"

# Container registry (Basic SKU — cheapest paid tier; ACR has no free tier)
az acr create --resource-group "$RESOURCE_GROUP" --name "$ACR_NAME" --sku Basic

# Container Apps environment
az extension add --name containerapp --upgrade
az provider register --namespace Microsoft.App
az provider register --namespace Microsoft.OperationalInsights
az containerapp env create --name "$ENVIRONMENT_NAME" --resource-group "$RESOURCE_GROUP" --location "$LOCATION"

# Placeholder container app — the workflow only ever updates its image afterwards.
# min-replicas 0 (scale-to-zero) keeps this within the Container Apps Consumption
# free monthly grant (180,000 vCPU-seconds / 360,000 GiB-seconds / 2M requests):
# the app costs nothing while idle and cold-starts on the next request.
az containerapp create \
  --name "$APP_NAME" \
  --resource-group "$RESOURCE_GROUP" \
  --environment "$ENVIRONMENT_NAME" \
  --image mcr.microsoft.com/k8se/quickstart:latest \
  --target-port 8080 \
  --ingress external \
  --min-replicas 0 --max-replicas 1

# Let the app pull from ACR using its own managed identity
az containerapp identity assign --name "$APP_NAME" --resource-group "$RESOURCE_GROUP" --system-assigned
PRINCIPAL_ID=$(az containerapp identity show --name "$APP_NAME" --resource-group "$RESOURCE_GROUP" --query principalId -o tsv)
ACR_ID=$(az acr show --name "$ACR_NAME" --query id -o tsv)
az role assignment create --assignee "$PRINCIPAL_ID" --role AcrPull --scope "$ACR_ID"
az containerapp registry set --name "$APP_NAME" --resource-group "$RESOURCE_GROUP" --server "$ACR_NAME.azurecr.io" --identity system
```

### Azure AD app registration for GitHub OIDC

```bash
APP_ID=$(az ad app create --display-name "gh-actions-test-publish-data-protos" --query appId -o tsv)
az ad sp create --id "$APP_ID"

az ad app federated-credential create --id "$APP_ID" --parameters '{
  "name": "github-main-branch",
  "issuer": "https://token.actions.githubusercontent.com",
  "subject": "repo:Subhajitdas298/test-publish-data-protos:ref:refs/heads/main",
  "audiences": ["api://AzureADTokenExchange"]
}'
```

> **Note:** if this GitHub org/repo has the "use unique repository/owner ID in the
> subject claim" OIDC setting enabled, the actual subject GitHub sends is
> `repo:<owner>@<owner_id>/<repo>@<repo_id>:ref:refs/heads/main` instead of the plain
> name form above — check the workflow's `azure/login` step for an `AADSTS700213`
> error to find the exact subject it presented, then update the federated credential
> to match. This repo's federated credential uses:
> `repo:Subhajitdas298@20024190/test-publish-data-protos@1313942807:ref:refs/heads/main`.

# Let the CI identity push images and update the container app
az role assignment create --assignee "$APP_ID" --role AcrPush --scope "$ACR_ID"
CONTAINERAPP_ID=$(az containerapp show --name "$APP_NAME" --resource-group "$RESOURCE_GROUP" --query id -o tsv)
az role assignment create --assignee "$APP_ID" --role "Container Apps Contributor" --scope "$CONTAINERAPP_ID"
```

### Provisioned resources

The Azure resources below already exist in subscription `cfb23074-9c21-4bc5-aecb-4845d97a147e`
("Azure subscription 1"), resource group **`data-protos`** (region `eastus`), created to
minimize cost (Consumption-based Container Apps, scale-to-zero, Basic-tier ACR — the
cheapest tier available since ACR has no free tier):

| Resource                       | Name                                                                                   |
|----------------------------------|-------------------------------------------------------------------------------------------|
| Resource group                  | `data-protos`                                                                          |
| Container Registry (Basic)      | `dataprotosacr2477` (`dataprotosacr2477.azurecr.io`)                                   |
| Container Apps environment      | `cae-data-protos`                                                                      |
| Container App (min-replicas 0)  | `data-protos` — `https://data-protos.gentlepond-37bc0af9.eastus.azurecontainerapps.io` |
| AD app registration (OIDC)      | `gh-actions-data-protos` (client ID `23d82297-02c5-4608-a020-66c6af602b57`)            |

The AD app has a federated credential scoped to
`repo:Subhajitdas298@20024190/test-publish-data-protos@1313942807:ref:refs/heads/main`
(this repo has GitHub's immutable-ID OIDC subject format enabled — see the note above),
`AcrPush` on the registry, and `Container Apps Contributor` on the container app. The
container app's own system-assigned identity has `AcrPull` on the registry so it can
pull images.

### GitHub repo configuration

**Settings → Secrets and variables → Actions → Secrets:**

| Secret                 | Value                                              |
|-------------------------|-----------------------------------------------------|
| `AZURE_CLIENT_ID`       | `23d82297-02c5-4608-a020-66c6af602b57`              |
| `AZURE_TENANT_ID`       | `86c9c0f2-9014-48a2-99e7-785b23ee2769`              |
| `AZURE_SUBSCRIPTION_ID` | `cfb23074-9c21-4bc5-aecb-4845d97a147e`              |
| `PACKAGES_READ_TOKEN`   | a GitHub PAT with `read:packages`, so the workflow can resolve `test-data-protos` from GitHub Packages |

**Settings → Secrets and variables → Actions → Variables:**

| Variable                        | Value                                  |
|-----------------------------------|-------------------------------------------|
| `AZURE_CONTAINER_REGISTRY_NAME` | `dataprotosacr2477`                      |
| `AZURE_RESOURCE_GROUP`          | `data-protos`                            |
| `AZURE_CONTAINER_APP_NAME`      | `data-protos`                            |

These can't be set via the GitHub tools available to this session (setting an Actions
secret requires client-side encryption with the repo's public key), so add them yourself
in the GitHub UI. Once set, any push to `main` (including a merged PR) triggers the workflow.
