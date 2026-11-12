FROM jenkins/jenkins:lts-jdk21
COPY init.groovy.d/ /usr/share/jenkins/ref/init.groovy.d/
USER root
RUN sed -i 's#http://deb.debian.org#https://deb.debian.org#g' /etc/apt/sources.list.d/debian.sources \
    && apt-get update && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/*
USER jenkins
RUN jenkins-plugin-cli --plugins workflow-aggregator git junit powershell credentials-binding plain-credentials
