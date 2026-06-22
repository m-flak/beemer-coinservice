# deploy-app

Ansible playbook that builds and deploys the coinservice JAR to a Linux host via systemd. Invoked by GitHub Actions on every PR merge to `main` or `development`, or on manual workflow dispatch.

## Prerequisites

**setup-host must have run successfully first.** The deploy playbook assumes Java 26 is installed, the `deploy` user exists with its SSH key authorized, and SSH is listening on the non-standard port.

## One-time setup before the first GHA run

### 1. Create GitHub environments

In repo **Settings → Environments**, create two environments:

- `production`
- `development`

Optionally configure required reviewers on `production` to gate deploys behind manual approval.

### 2. Set GitHub repository secrets

In repo **Settings → Secrets and variables → Actions → Secrets**:

| Secret | Value |
|---|---|
| `AZURE_CLIENT_ID` | Client ID of the service principal used for OIDC login to Azure |
| `AZURE_TENANT_ID` | Azure tenant ID |
| `AZURE_SUBSCRIPTION_ID` | Azure subscription ID |
| `AZURE_KEY_VAULT_NAME` | Name of the Key Vault (not the full URL, just the name) |

### 3. Set GitHub repository variables

In repo **Settings → Secrets and variables → Actions → Variables**:

| Variable | Value |
|---|---|
| `PRODUCTION_HOST` | IP of the production app host |
| `DEVELOPMENT_HOST` | IP of the development app host |

### 4. Configure Azure OIDC federated credentials

On the app registration used for GHA login, add two federated credentials (one per environment):

- **Entity type:** Environment
- **GitHub organization/repo:** `your-org/coinservice`
- **Environment:** `production` (repeat for `development`)

The service principal needs **Key Vault Secrets User** role on the Key Vault so the workflow can fetch secrets.

### 5. Populate Azure Key Vault secrets

| Secret name | Value |
|---|---|
| `DEPLOY-SSH-KEY` | Private SSH key for the `deploy` user (must match the public key used in setup-host) |
| `SSH-PORT` | The non-standard SSH port configured during setup-host |
| `AZURE-CLIENT-ID` | Azure client ID for the coinservice application |
| `AZURE-CLIENT-SECRET` | Azure client secret for the coinservice application |

### 6. Ensure Azure App Configuration store exists

The application imports config from `https://ac-coinservice.azconfig.io` on startup. This store must exist and contain the required keys before the first deploy, otherwise the app will fail to start and the health check will time out.

## How GHA invokes the playbook

The workflow builds the JAR, fetches secrets from Key Vault, then runs:

```bash
ansible-playbook \
  -i "${HOST}," \
  --private-key ~/.ssh/deploy_key \
  -u deploy \
  -e ansible_port="$SSH_PORT" \
  -e coinservice_jar_src="build/libs/coinservice-$VERSION.jar" \
  -e coinservice_azure_client_id="$CLIENT_ID" \
  -e coinservice_azure_client_secret="$CLIENT_SECRET" \
  cd/deploy-app/deploy.yml
```

## Running manually

Useful for debugging or one-off deploys. Build the JAR first, then:

```bash
ansible-playbook \
  -i "YOUR.VPS.IP," \
  --private-key ~/.ssh/your_deploy_key \
  -u deploy \
  -e ansible_port=XXXXX \
  -e coinservice_jar_src=build/libs/coinservice-X.Y.Z.jar \
  -e coinservice_azure_client_id=YOUR_CLIENT_ID \
  -e coinservice_azure_client_secret=YOUR_CLIENT_SECRET \
  cd/deploy-app/deploy.yml
```

## What the playbook does

1. Verifies Java 26 is present on the target host
2. Creates the `coinservice` system user, deploy directory (`/opt/coinservice`), and log directory (`/var/log/coinservice`)
3. Writes `/etc/coinservice/env` from the env template (Azure credentials, etc.)
4. Copies the JAR to `/opt/coinservice/coinservice.jar`
5. Installs the systemd unit file and restarts the service
6. Waits for the service to come up and verifies the `/actuator/health` endpoint returns 200
