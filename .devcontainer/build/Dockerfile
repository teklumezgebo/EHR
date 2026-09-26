FROM mcr.microsoft.com/devcontainers/base:ubuntu-24.04

# Toolchains are copied from their official images so versions follow the image tags.
COPY --from=eclipse-temurin:21-jdk-noble /opt/java/openjdk /opt/java/openjdk
COPY --from=node:24-bookworm-slim /usr/local/bin/node /usr/local/bin/node
COPY --from=node:24-bookworm-slim /usr/local/lib/node_modules /usr/local/lib/node_modules
COPY --from=hashicorp/terraform:1.15 /bin/terraform /usr/local/bin/terraform

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=$JAVA_HOME/bin:$PATH

# Volume mount points are created up front so the volumes are owned by vscode, not root.
RUN ln -s ../lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
    && ln -s ../lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx \
    && curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg \
        -o /usr/share/keyrings/githubcli-archive-keyring.gpg \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" \
        > /etc/apt/sources.list.d/github-cli.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends gh postgresql-client-16 jq unzip \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /home/vscode/.gradle /home/vscode/.claude \
    && chown vscode:vscode /home/vscode/.gradle /home/vscode/.claude
