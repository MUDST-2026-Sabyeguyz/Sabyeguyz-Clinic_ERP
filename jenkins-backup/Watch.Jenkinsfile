pipeline {
  agent { node { label 'built-in'; customWorkspace '/var/jenkins_home/clinic-watch-workspace' } }
  options { skipDefaultCheckout(); disableConcurrentBuilds(); timeout(time: 10, unit: 'MINUTES'); buildDiscarder(logRotator(numToKeepStr: '20')) }
  triggers { cron('H/5 * * * *') }
  stages {
    stage('Fetch branch changes') {
      steps {
        checkout([$class: 'GitSCM', branches: [[name: '*/develop']],
          userRemoteConfigs: [[url: 'https://github.com/MUDST-2026-Sabyeguyz/Sabyeguyz-Clinic_ERP.git', credentialsId: 'clinic-github', refspec: '+refs/heads/*:refs/remotes/origin/*']]])
        sh '''#!/bin/bash
set -euo pipefail
git bundle create /var/jenkins_home/clinic-watch.bundle --all
git --git-dir=/var/jenkins_home/clinic-source.git fetch /var/jenkins_home/clinic-watch.bundle '+refs/remotes/origin/*:refs/remotes/origin/*'
python3 /opt/clinic/ci/watch.py > new-commits.txt
'''
      }
    }
    stage('Queue new commits') {
      steps {
        script {
          def commits = readFile('new-commits.txt').trim()
          if (commits) {
            commits.split('\n').each { sha ->
              build job: 'Clinic-CI', wait: false, parameters: [string(name: 'SOURCE_SHA', value: sha), string(name: 'WORKFLOWS', value: '')]
            }
          }
        }
        sh "python3 -c \"from pathlib import Path; Path('/var/jenkins_home/clinic-watch-state.pending.json').replace('/var/jenkins_home/clinic-watch-state.json')\""
      }
    }
  }
}
