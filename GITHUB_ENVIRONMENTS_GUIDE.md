# GitHub Environments Guide

This document provides instructions on how to set up and switch between different environments (development, staging, production) in GitHub Actions, with a focus on managing environment-specific secrets.

## What are GitHub Environments?

GitHub Environments are a feature that allows you to configure different deployment environments (e.g., development, staging, production) with specific protection rules and secrets. This enables you to:

- Store environment-specific secrets
- Set up required reviewers for deployments
- Configure wait timers before deployments
- Restrict which branches can deploy to specific environments

## Setting Up Environments in GitHub

### 1. Create Environments

1. Go to your GitHub repository
2. Click on "Settings" > "Environments"
3. Click on "New environment"
4. Name your environment (e.g., "development", "staging", "production")
5. Configure protection rules if needed:
   - Required reviewers
   - Wait timer
   - Deployment branches (restrict which branches can deploy to this environment)
6. Click "Configure environment"

### 2. Add Environment Secrets

For each environment, you can add environment-specific secrets:

1. Go to your GitHub repository
2. Click on "Settings" > "Environments"
3. Click on the environment you want to configure
4. Under "Environment secrets", click "Add secret"
5. Enter the secret name and value
6. Click "Add secret"

Common secrets you might want to set per environment:

- `FIREBASE_DEBUG_APP_ID` and `FIREBASE_RELEASE_APP_ID` (different for each environment)
- `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` (might be different for development vs. production)
- `API_ENDPOINT` (different URLs for dev, staging, and production)
- `FIREBASE_TOKEN` (might use different Firebase projects for different environments)

## Using Environments in GitHub Actions

To use an environment in a GitHub Actions workflow, add the `environment` key to your job:

```yaml
jobs:
  deploy-to-staging:
    runs-on: ubuntu-latest
    environment: staging  # Specify the environment name here
    
    steps:
      - uses: actions/checkout@v4
      
      # Now you can access environment-specific secrets
      - name: Deploy to Firebase
        env:
          FIREBASE_TOKEN: ${{ secrets.FIREBASE_TOKEN }}
          FIREBASE_APP_ID: ${{ secrets.FIREBASE_APP_ID }}
        run: |
          firebase deploy --token "$FIREBASE_TOKEN" --app "$FIREBASE_APP_ID"
```

## Example: Multi-Environment Workflow

Here's an example of a workflow that can deploy to different environments:

```yaml
name: Deploy App

on:
  workflow_dispatch:
    inputs:
      environment:
        description: 'Environment to deploy to'
        required: true
        default: 'development'
        type: choice
        options:
          - development
          - staging
          - production
      releaseNotes:
        description: 'Release notes'
        required: true
        default: 'New release'

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    environment: ${{ github.event.inputs.environment }}  # Dynamic environment selection
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          
      - name: Build APK
        env:
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: ./gradlew assembleRelease
        
      - name: Deploy to Firebase
        env:
          FIREBASE_TOKEN: ${{ secrets.FIREBASE_TOKEN }}
          FIREBASE_APP_ID: ${{ secrets.FIREBASE_APP_ID }}
          RELEASE_NOTES: ${{ github.event.inputs.releaseNotes }}
        run: |
          npm install -g firebase-tools
          firebase appdistribution:distribute "app/build/outputs/apk/release/app-release.apk" \
            --app "$FIREBASE_APP_ID" \
            --release-notes "$RELEASE_NOTES" \
            --groups "testers" \
            --token "$FIREBASE_TOKEN"
```

## Modifying Existing Workflows to Use Environments

To modify the existing `release.yml` workflow to use environments:

1. Add an environment input parameter:
   ```yaml
   on:
     workflow_dispatch:
       inputs:
         environment:
           description: 'Environment to deploy to'
           required: true
           default: 'development'
           type: choice
           options:
             - development
             - staging
             - production
         buildType:
           description: 'Build type (debug or release)'
           required: true
           default: 'release'
           type: choice
           options:
             - debug
             - release
   ```

2. Update the job to use the selected environment:
   ```yaml
   jobs:
     build:
       runs-on: ubuntu-latest
       environment: ${{ github.event.inputs.environment }}
   ```

3. The workflow will now use the secrets from the selected environment.

## Best Practices

1. **Naming Convention**: Use consistent naming for secrets across environments to simplify workflow files.

2. **Documentation**: Document which secrets are required for each environment.

3. **Minimal Secrets**: Only store the secrets that are actually different between environments as environment secrets. Common secrets should be stored as repository secrets.

4. **Protection Rules**: Use protection rules for production environments to prevent accidental deployments.

5. **Branch Protection**: Combine environment protection with branch protection rules for a comprehensive security strategy.

## Troubleshooting

### Common Issues

1. **Secret Not Available**: If a secret is not available in an environment, the workflow will fail. Make sure all required secrets are set for each environment.

2. **Environment Not Found**: If the specified environment doesn't exist, the workflow will fail. Make sure to create all environments before using them in workflows.

3. **Permission Issues**: Users need write permission to the repository to deploy to environments. Administrators may need to adjust repository permissions.

### Debugging Tips

1. Use `echo "Using environment: ${{ github.event.inputs.environment }}"` to verify which environment is being used.

2. Check the GitHub Actions logs for any error messages related to missing secrets or environment issues.

3. Test with a simple workflow that just prints environment information before implementing complex deployment logic.

## Additional Resources

- [GitHub Environments Documentation](https://docs.github.com/en/actions/deployment/targeting-different-environments/using-environments-for-deployment)
- [GitHub Secrets Documentation](https://docs.github.com/en/actions/security-guides/encrypted-secrets)
- [GitHub Actions Workflow Syntax](https://docs.github.com/en/actions/using-workflows/workflow-syntax-for-github-actions)