pipeline {
  agent any
  options { disableConcurrentBuilds(); skipDefaultCheckout(); timeout(time: 180, unit: 'MINUTES'); buildDiscarder(logRotator(numToKeepStr: '500')) }
  parameters {
    string(name: 'SOURCE_SHA', defaultValue: '', description: 'Exact commit SHA to verify')
    string(name: 'WORKFLOWS', defaultValue: '', description: 'Workflow filenames, comma separated; blank = all')
  }
  stages {
    stage('Validate immutable source') {
      steps {
        script {
          if (!(params.SOURCE_SHA ==~ /[0-9a-f]{40}/)) { error('SOURCE_SHA must be a full commit SHA') }
          if (!(params.WORKFLOWS ==~ /[a-zA-Z0-9_.\/,\-]*/)) { error('Invalid workflow list') }
          currentBuild.displayName = "#${env.BUILD_NUMBER} ${params.SOURCE_SHA.take(12)}"
        }
        sh 'git --git-dir=/var/jenkins_home/clinic-source.git cat-file -e "$SOURCE_SHA^{commit}"'
      }
    }
    stage('Replay original CI on isolated worker') {
      steps {
        sh '''#!/bin/bash
set -euo pipefail
export WORKFLOWS="${WORKFLOWS:-}"
mkdir -p "$WORKSPACE/results" /var/jenkins_home/ci-worker-home
docker run --rm --init --name "clinic-replay-$BUILD_NUMBER" --network host \
  --user 1000:1000 \
  -e DOCKER_HOST=tcp://127.0.0.1:2375 -e BUILD_NUMBER -e SOURCE_SHA -e WORKFLOWS \
  -e HOME=/var/jenkins_home/ci-worker-home -e CI=true \
  -e npm_config_maxsockets=8 -e npm_config_fetch_timeout=60000 -e npm_config_fetch_retries=2 \
  -e 'MAVEN_OPTS=-Dmaven.repo.local=/var/jenkins_home/ci-worker-home/.m2/repository -Xmx768m' \
  -v "$WORKSPACE:$WORKSPACE" \
  -v /var/jenkins_home/clinic-source.git:/var/jenkins_home/clinic-source.git:ro \
  -v /var/jenkins_home/ci-worker-home:/var/jenkins_home/ci-worker-home \
  -v /opt/clinic/replay.py:/opt/clinic/replay.py:ro \
  --entrypoint python3 clinic-ci-worker:local /opt/clinic/replay.py \
  --sha "$SOURCE_SHA" --workflows "$WORKFLOWS" --output "$WORKSPACE/results/$BUILD_NUMBER"
'''
      }
    }
  }
  post {
    always {
      sh 'docker stop -t 20 "clinic-replay-$BUILD_NUMBER" >/dev/null 2>&1 || true'
      sh 'docker rm -f "clinic-replay-$BUILD_NUMBER" >/dev/null 2>&1 || true'
      junit allowEmptyResults: true, testResults: "results/${env.BUILD_NUMBER}/summary.junit.xml"
      archiveArtifacts artifacts: "results/${env.BUILD_NUMBER}/**", allowEmptyArchive: true
      script {
        if (params.SOURCE_SHA ==~ /[0-9a-f]{40}/) {
          def context = params.WORKFLOWS.trim() ? 'jenkins/actions-replay' : 'jenkins/clinic-ci'
          writeFile file: 'commit-status.json', text: groovy.json.JsonOutput.toJson([
            state: currentBuild.currentResult == 'SUCCESS' ? 'success' : 'failure',
            context: context, target_url: env.BUILD_URL,
            description: "Jenkins #${env.BUILD_NUMBER}: ${currentBuild.currentResult} (see scope and tool differences in artifacts)"
          ])
          withCredentials([usernamePassword(credentialsId: 'clinic-github', usernameVariable: 'CI_GITHUB_USER', passwordVariable: 'CI_GITHUB_TOKEN')]) {
            sh '''#!/bin/bash
set +x
curl --fail --silent --show-error --output /dev/null \
  -H "Authorization: Bearer $CI_GITHUB_TOKEN" -H 'Accept: application/vnd.github+json' \
  -X POST --data-binary @commit-status.json \
  "https://api.github.com/repos/MUDST-2026-Sabyeguyz/Sabyeguyz-Clinic_ERP/statuses/$SOURCE_SHA"
'''
          }
        }
      }
    }
  }
}
