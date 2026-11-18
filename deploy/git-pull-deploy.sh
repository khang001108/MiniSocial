#!/bin/sh
set -eu
# Safe local operation that Xubi can invoke as a deployment script.
cd /opt/minisocial
# Must be a configured repository with an explicit trusted branch.
git fetch --prune origin main
git merge --ff-only origin/main
cd deploy
docker compose up -d --build
curl -fsS http://127.0.0.1:8087/health
