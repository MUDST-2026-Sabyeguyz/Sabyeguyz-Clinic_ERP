pipeline {
    agent { label 'clinic-deployer' }

    options {
        disableConcurrentBuilds()
        skipDefaultCheckout(true)
        timeout(time: 70, unit: 'MINUTES')
    }

    triggers {
        pollSCM('H/2 * * * *')
    }

    environment {
        K8S_CONTEXT = 'docker-desktop'
        K8S_NAMESPACE = 'clinic-presentation-20261112'
    }

    stages {
        stage('Checkout reviewed branch') {
            steps {
                deleteDir()
                checkout scm
                script {
                    env.SOURCE_SHA = bat(returnStdout: true, script: '@git rev-parse HEAD').trim().toLowerCase()
                    if (!(env.SOURCE_SHA ==~ /[0-9a-f]{40}/)) { error('Checkout did not resolve to a full immutable Git SHA') }
                    env.BACKEND_IMAGE = "clinic-presentation-backend:${env.SOURCE_SHA}"
                    env.FRONTEND_IMAGE = "clinic-presentation-frontend:${env.SOURCE_SHA}"
                }
            }
        }

        stage('Tool and target guard') {
            steps {
                withCredentials([file(credentialsId: 'clinic-kubernetes-config', variable: 'KUBECONFIG')]) {
                    powershell '''
                        $ErrorActionPreference = 'Stop'
                        if ((git rev-parse HEAD).Trim() -ne $env:SOURCE_SHA) { throw 'Checkout SHA mismatch' }
                        node --version
                        java -version
                        mvn -version
                        docker info --format 'Docker={{.ServerVersion}}/{{.OSType}}'
                        kubectl --kubeconfig $env:KUBECONFIG --context $env:K8S_CONTEXT -n $env:K8S_NAMESPACE get deployment/backend deployment/frontend
                        if ($LASTEXITCODE -ne 0) { throw 'The dedicated Kubernetes namespace is not ready' }
                    '''
                }
            }
        }

        stage('Frontend tests and build') {
            steps {
                powershell '''
                    $ErrorActionPreference = 'Stop'
                    $testImage = "clinic-presentation-frontend-tests:$env:SOURCE_SHA"
                    docker build --tag $testImage --file frontend/Dockerfile.ci-tests frontend
                    if ($LASTEXITCODE -ne 0) { throw 'Frontend test image build failed' }
                    docker run --rm --memory 1g $testImage
                    if ($LASTEXITCODE -ne 0) { throw 'Frontend unit tests failed' }
                '''
            }
        }

        stage('Backend tests') {
            steps {
                dir('backend') {
                    bat 'mvn --batch-mode --no-transfer-progress verify'
                }
            }
        }

        stage('Build source-tagged images') {
            steps {
                powershell '''
                    $ErrorActionPreference = 'Stop'
                    docker build --tag $env:BACKEND_IMAGE backend
                    if ($LASTEXITCODE -ne 0) { throw 'Backend image build failed' }
                    docker build --tag $env:FRONTEND_IMAGE frontend
                    if ($LASTEXITCODE -ne 0) { throw 'Frontend image build failed' }
                '''
            }
        }

        stage('Deploy and verify') {
            steps {
                withCredentials([
                    file(credentialsId: 'clinic-kubernetes-config', variable: 'KUBECONFIG'),
                    string(credentialsId: 'clinic-demo-manager-password', variable: 'DEMO_MANAGER_PASSWORD')
                ]) {
                    powershell '''
                        $ErrorActionPreference = 'Stop'
                        $oldBackend = kubectl --kubeconfig $env:KUBECONFIG --context $env:K8S_CONTEXT -n $env:K8S_NAMESPACE get deployment/backend -o jsonpath='{.spec.template.spec.containers[?(@.name=="backend")].image}'
                        if ($LASTEXITCODE -ne 0) { throw 'Cannot read existing backend deployment' }
                        $oldFrontend = kubectl --kubeconfig $env:KUBECONFIG --context $env:K8S_CONTEXT -n $env:K8S_NAMESPACE get deployment/frontend -o jsonpath='{.spec.template.spec.containers[?(@.name=="frontend")].image}'
                        if ($LASTEXITCODE -ne 0) { throw 'Cannot read existing frontend deployment' }
                        try {
                            kubectl --kubeconfig $env:KUBECONFIG --context $env:K8S_CONTEXT -n $env:K8S_NAMESPACE set image deployment/backend backend=$env:BACKEND_IMAGE
                            if ($LASTEXITCODE -ne 0) { throw 'Backend image update failed' }
                            kubectl --kubeconfig $env:KUBECONFIG --context $env:K8S_CONTEXT -n $env:K8S_NAMESPACE set image deployment/frontend frontend=$env:FRONTEND_IMAGE
                            if ($LASTEXITCODE -ne 0) { throw 'Frontend image update failed' }
                            kubectl --kubeconfig $env:KUBECONFIG --context $env:K8S_CONTEXT -n $env:K8S_NAMESPACE rollout status deployment/backend --timeout=300s
                            if ($LASTEXITCODE -ne 0) { throw 'Backend rollout failed' }
                            kubectl --kubeconfig $env:KUBECONFIG --context $env:K8S_CONTEXT -n $env:K8S_NAMESPACE rollout status deployment/frontend --timeout=300s
                            if ($LASTEXITCODE -ne 0) { throw 'Frontend rollout failed' }

                            $port = 28182
                            $forward = Start-Process -FilePath 'kubectl' -ArgumentList @('--kubeconfig', $env:KUBECONFIG, '--context', $env:K8S_CONTEXT, '-n', $env:K8S_NAMESPACE, 'port-forward', '--address', '127.0.0.1', 'service/backend', "$port`:8080") -PassThru -WindowStyle Hidden
                            try {
                                $ready = $false
                                for ($i = 0; $i -lt 40; $i++) {
                                    Start-Sleep -Seconds 2
                                    try { $health = Invoke-RestMethod "http://127.0.0.1:$port/actuator/health" -TimeoutSec 3; if ($health.status -eq 'UP') { $ready = $true; break } } catch {}
                                }
                                if (-not $ready) { throw 'Backend health check did not become UP' }
                                $login = Invoke-RestMethod "http://127.0.0.1:$port/api/auth/login" -Method Post -ContentType 'application/json' -Body (@{username='manager'; password=$env:DEMO_MANAGER_PASSWORD} | ConvertTo-Json -Compress) -TimeoutSec 15
                                if (-not $login.token) { throw 'Authenticated demo login failed' }
                                $headers = @{ Authorization = "Bearer $($login.token)" }
                                $null = Invoke-RestMethod "http://127.0.0.1:$port/api/patients?size=5" -Headers $headers -TimeoutSec 15
                                $null = Invoke-RestMethod "http://127.0.0.1:$port/api/appointments?size=5" -Headers $headers -TimeoutSec 15
                                $null = Invoke-RestMethod "http://127.0.0.1:$port/api/reports/revenue?from=2026-11-01&to=2026-11-30&branchCode=01" -Headers $headers -TimeoutSec 15
                            } finally {
                                if ($forward -and -not $forward.HasExited) { Stop-Process -Id $forward.Id -Force }
                            }
                            kubectl --kubeconfig $env:KUBECONFIG --context $env:K8S_CONTEXT -n $env:K8S_NAMESPACE get deployments,pods,services,pvc -o wide
                            if ($LASTEXITCODE -ne 0) { throw 'Cannot collect deployment evidence' }
                        } catch {
                            Write-Warning 'Deployment verification failed; restoring prior application images.'
                            if ($oldBackend) { kubectl --kubeconfig $env:KUBECONFIG --context $env:K8S_CONTEXT -n $env:K8S_NAMESPACE set image deployment/backend backend=$oldBackend | Out-Null }
                            if ($oldFrontend) { kubectl --kubeconfig $env:KUBECONFIG --context $env:K8S_CONTEXT -n $env:K8S_NAMESPACE set image deployment/frontend frontend=$oldFrontend | Out-Null }
                            throw
                        }
                    '''
                }
            }
        }
    }
}
