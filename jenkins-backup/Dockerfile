FROM node:24-bookworm-slim AS node
FROM docker:27-cli AS docker
FROM jenkins/jenkins:lts-jdk21
USER root
COPY --from=docker /usr/local/bin/docker /usr/local/bin/docker
COPY --from=docker /usr/local/libexec/docker/cli-plugins /usr/local/libexec/docker/cli-plugins
COPY --from=node /usr/local/bin/node /usr/local/bin/node
COPY --from=node /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -s /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm && ln -s /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx \
    && apt-get update && apt-get install -y --no-install-recommends maven postgresql-client chromium libgtk-3-0 libgbm1 libnotify4 libnss3 libxss1 libasound2 libxtst6 xauth xvfb \
    && rm -rf /var/lib/apt/lists/*
USER jenkins
RUN jenkins-plugin-cli --plugins workflow-aggregator git junit
