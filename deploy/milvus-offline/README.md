# Milvus Offline Package

This directory contains the files used to build and install an offline Milvus Standalone package for the middleware resource manager project.

Recommended workflow:

1. On an internet-connected machine:

   ```bash
   cd deploy/milvus-offline
   chmod +x scripts/*.sh
   ./scripts/prepare-offline-package.sh
   ```

2. Copy `deploy/milvus-offline/dist/milvus-offline-package.tar.gz` to the intranet server.

3. On the intranet server:

   ```bash
   tar -xzf milvus-offline-package.tar.gz
   cd milvus-offline-package
   chmod +x scripts/*.sh
   ./scripts/load-images.sh
   vi .env
   ./scripts/start.sh
   ./scripts/healthcheck.sh
   ```

