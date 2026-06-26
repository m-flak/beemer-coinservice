#!/bin/bash
set -e

AZURE_CLIENT_ID=$(az keyvault secret show --vault-name kv-coinservice --name AZURE-CLIENT-ID --query value -o tsv) \
AZURE_CLIENT_SECRET=$(az keyvault secret show --vault-name kv-coinservice --name AZURE-CLIENT-SECRET --query value -o tsv) \
SPRING_PROFILES_ACTIVE=development,local \
LOGGING_LEVEL_COM_AZURE=TRACE \
./gradlew bootRun $1
