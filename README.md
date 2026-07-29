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
- **`DataController`** — exposes both services on a single URL, differentiated purely by
  the `Accept` header (HTTP content negotiation).

## Data shape

The generated dataset (a `Root` protobuf message) consists of:

- **10 days** of data (`DataEntry.dates`, one `DateRecord` per day)
- Each day has **26 fields** (`a`–`z`, matching the proto definition)
- Each field contains **10,000 randomly generated `double` records**

That's `10 * 26 * 10,000 = 2,600,000` values, generated once and reused for every request.

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

## Deployment (Azure Container Apps)

[`.github/workflows/deploy.yml`](.github/workflows/deploy.yml) builds the jar, builds a
Docker image (see [`Dockerfile`](Dockerfile)), pushes it to Azure Container Registry, and
updates the Azure Container App to the new image, on every push to `main` (which includes
merging a PR into `main`) and via manual `workflow_dispatch`.

It authenticates to Azure with OIDC (`azure/login`, no client secret stored in GitHub).

### Azure resources you need to create once

```bash
SUBSCRIPTION_ID=<your-subscription-id>
RESOURCE_GROUP=rg-test-publish-data-protos
LOCATION=eastus
ACR_NAME=<globally-unique-name>          # e.g. testpublishdataprotosacr
ENVIRONMENT_NAME=cae-test-publish-data-protos
APP_NAME=test-publish-data-protos

az account set --subscription "$SUBSCRIPTION_ID"

# Resource group
az group create --name "$RESOURCE_GROUP" --location "$LOCATION"

# Container registry
az acr create --resource-group "$RESOURCE_GROUP" --name "$ACR_NAME" --sku Basic

# Container Apps environment
az extension add --name containerapp --upgrade
az provider register --namespace Microsoft.App
az provider register --namespace Microsoft.OperationalInsights
az containerapp env create --name "$ENVIRONMENT_NAME" --resource-group "$RESOURCE_GROUP" --location "$LOCATION"

# Placeholder container app — the workflow only ever updates its image afterwards
az containerapp create \
  --name "$APP_NAME" \
  --resource-group "$RESOURCE_GROUP" \
  --environment "$ENVIRONMENT_NAME" \
  --image mcr.microsoft.com/k8se/quickstart:latest \
  --target-port 8080 \
  --ingress external \
  --min-replicas 1 --max-replicas 1

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

# Let the CI identity push images and update the container app
az role assignment create --assignee "$APP_ID" --role AcrPush --scope "$ACR_ID"
CONTAINERAPP_ID=$(az containerapp show --name "$APP_NAME" --resource-group "$RESOURCE_GROUP" --query id -o tsv)
az role assignment create --assignee "$APP_ID" --role "Container Apps Contributor" --scope "$CONTAINERAPP_ID"
```

### GitHub repo configuration

**Settings → Secrets and variables → Actions → Secrets:**

| Secret                 | Value                                              |
|-------------------------|-----------------------------------------------------|
| `AZURE_CLIENT_ID`       | `$APP_ID` from above                                |
| `AZURE_TENANT_ID`       | your Azure AD tenant ID                             |
| `AZURE_SUBSCRIPTION_ID` | `$SUBSCRIPTION_ID` from above                       |
| `PACKAGES_READ_TOKEN`   | a GitHub PAT with `read:packages`, so the workflow can resolve `test-data-protos` from GitHub Packages |

**Settings → Secrets and variables → Actions → Variables:**

| Variable                        | Value                                  |
|-----------------------------------|-------------------------------------------|
| `AZURE_CONTAINER_REGISTRY_NAME` | `$ACR_NAME` (registry name only, no `.azurecr.io`) |
| `AZURE_RESOURCE_GROUP`          | `$RESOURCE_GROUP`                       |
| `AZURE_CONTAINER_APP_NAME`      | `$APP_NAME`                             |

Once those are set, any push to `main` (including a merged PR) triggers the workflow.
