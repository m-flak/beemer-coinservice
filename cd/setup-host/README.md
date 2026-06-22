# setup-host

One-time bootstrap playbook for all coinservice VPS hosts. Run this against a fresh host before any deploys.

## Architecture

Four hosts total:

| Group | Count | Purpose |
|---|---|---|
| `coinservice_app_hosts` | 2 | Spring Boot API on 8443 (SSL). NAT redirects 443 → 8443. |
| `coinservice_nginx_hosts` | 2 | nginx serving the Angular SPA on 443. Production and development. |

The API and UI are on separate subdomains. nginx has no knowledge of the backend.

## Prerequisites

Python 3 is required on the target hosts. The playbook installs it automatically via `apt` if missing, so a truly bare VPS is fine.

Install Ansible and the required collections:

```bash
pip install ansible
ansible-galaxy collection install -r requirements.yml
```

## Manual steps before running

### 1. Create your inventory

```bash
cp inventory/hosts.example inventory/hosts
```

Edit `inventory/hosts` and replace all placeholder values:
- `YOUR.VPS.IP.*` — the real IP for each host
- `YOUR.PRODUCTION.DOMAIN` / `YOUR.DEVELOPMENT.DOMAIN` — the FQDNs nginx will serve

`inventory/hosts` is gitignored — do not commit it.

### 2. Generate SSH keys

Two key pairs are needed — one for the CI/CD `deploy` user, one for your personal `admin` (break-glass) account.

```bash
# Deploy key (used by GitHub Actions)
ssh-keygen -t ed25519 -C "coinservice-deploy" -f ~/.ssh/coinservice_deploy

# Admin key (your personal break-glass access)
ssh-keygen -t ed25519 -C "coinservice-admin" -f ~/.ssh/coinservice_admin
```

The **public keys** (`*.pub`) are passed to the playbook via `-e deploy_ssh_public_key` and `-e admin_ssh_public_key`.

The **deploy private key** (`~/.ssh/coinservice_deploy`) must be stored in Azure Key Vault as the secret `DEPLOY-SSH-KEY` so GitHub Actions can retrieve it at deploy time. Do this before running the deploy workflow:

```bash
az keyvault secret set \
  --vault-name YOUR_VAULT_NAME \
  --name DEPLOY-SSH-KEY \
  --file ~/.ssh/coinservice_deploy
```

The **admin private key** (`~/.ssh/coinservice_admin`) stays on your local machine only.

### 3. Drop SSL certificates into `files/`

Name the files after the inventory hostname for each nginx host:

```
files/
  coinservice-nginx-production-fullchain.pem
  coinservice-nginx-production-privkey.pem
  coinservice-nginx-development-fullchain.pem
  coinservice-nginx-development-privkey.pem
```

All `*.pem`, `*.crt`, and `*.key` files in `files/` are gitignored — do not commit them.

## Running the playbook

The playbook must be run on port 22 (fresh host, root login still enabled):

```bash
ansible-playbook -i inventory/hosts setup.yml \
  -e ssh_port=XXXXX \
  -e deploy_ssh_public_key="ssh-ed25519 AAAA..." \
  -e admin_user=matt \
  -e admin_ssh_public_key="ssh-ed25519 AAAA..."
```

To target only one group (e.g. to re-run just the nginx hosts):

```bash
ansible-playbook -i inventory/hosts setup.yml --limit coinservice_nginx_hosts \
  -e ssh_port=XXXXX \
  -e deploy_ssh_public_key="ssh-ed25519 AAAA..." \
  -e admin_user=matt \
  -e admin_ssh_public_key="ssh-ed25519 AAAA..."
```

To run only specific tasks by tag (`packages`, `java`, `ssh`, `firewall`, `nat`, `sysctl`, `nginx`, `ssl`):

```bash
ansible-playbook -i inventory/hosts setup.yml --tags nginx,ssl \
  -e ssh_port=XXXXX \
  ...
```

## Manual steps after running

### 1. Update ansible_port in your inventory

The playbook moves SSH off port 22. Update `inventory/hosts` so subsequent runs connect on the right port:

```ini
[coinservice_hosts:vars]
ansible_port=XXXXX   # set this to the ssh_port you passed above
```

### 2. Deploy the Angular SPA to the nginx hosts

The webroots are created but empty. Build and upload the Angular app before the UI is reachable:

- Production: `/var/www/coinservice/production`
- Development: `/var/www/coinservice/development`

### 3. Verify nginx is serving correctly

After deploying the SPA, spot-check each site:

```bash
curl -I https://YOUR.PRODUCTION.DOMAIN
curl -I https://YOUR.DEVELOPMENT.DOMAIN
```

## What the playbook does

- Updates all packages
- Installs Temurin JDK 26 (app hosts need it; nginx hosts get it too for simplicity)
- Sets the system timezone to UTC
- Creates `deploy` (CI/CD) and `admin` (break-glass) users with SSH key auth
- Creates the `coinservice` system user for running the API
- Hardens SSH: non-standard port, no root login, no password auth, restricted `AllowUsers`
- Configures ufw: default deny inbound, allow SSH and HTTPS only
- Adds 443 → 8443 NAT redirect on app hosts
- Applies network hardening sysctl parameters
- **nginx hosts only:** installs nginx, copies SSL certs, deploys nginx.conf, creates webroot
