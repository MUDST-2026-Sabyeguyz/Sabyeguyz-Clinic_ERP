# Run explicitly as the existing cluster administrator, never from the deployment workflow.
[CmdletBinding()]
param([ValidateSet('docker-desktop')][string]$Context = 'docker-desktop')
. "$PSScriptRoot/Common.ps1"
Protect-Directory $script:Runtime
function Admin { Invoke-Checked kubectl (@('--context',$Context,'--request-timeout=30s') + $args) }
$ns = $script:Namespace
$documents = @(
 @{apiVersion='v1';kind='Namespace';metadata=@{name=$ns;labels=@{'app.kubernetes.io/part-of'='clinic-actions-demo'}}},
 @{apiVersion='v1';kind='ServiceAccount';metadata=@{name='clinic-actions-runner';namespace=$ns}},
 @{apiVersion='rbac.authorization.k8s.io/v1';kind='Role';metadata=@{name='clinic-actions-deployer';namespace=$ns};rules=@(
   @{apiGroups=@('');resources=@('configmaps','services');verbs=@('get','list','watch','create','update','patch')},
   @{apiGroups=@('');resources=@('pods','pods/log','events');verbs=@('get','list','watch')},
   @{apiGroups=@('');resources=@('pods/portforward');verbs=@('create')},
   @{apiGroups=@('apps');resources=@('deployments','deployments/status','replicasets');verbs=@('get','list','watch','create','update','patch')},
   @{apiGroups=@('batch');resources=@('jobs');verbs=@('get','list','watch','create','update','patch','delete')}
 )},
 @{apiVersion='rbac.authorization.k8s.io/v1';kind='RoleBinding';metadata=@{name='clinic-actions-runner';namespace=$ns};subjects=@(@{kind='ServiceAccount';name='clinic-actions-runner';namespace=$ns});roleRef=@{apiGroup='rbac.authorization.k8s.io';kind='Role';name='clinic-actions-deployer'}},
 @{apiVersion='v1';kind='PersistentVolumeClaim';metadata=@{name='postgres-data';namespace=$ns};spec=@{accessModes=@('ReadWriteOnce');resources=@{requests=@{storage='2Gi'}}}}
)
$provision = Join-Path $script:Runtime 'admin-provision.json'
@{apiVersion='v1';kind='List';items=$documents} | ConvertTo-Json -Depth 30 | Set-Content $provision -Encoding utf8
Admin apply -f $provision
$credentialsPath = Join-Path $script:Runtime 'credentials.json'
if (-not (Test-Path $credentialsPath)) {
 $random = { [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant() }
 @{POSTGRES_PASSWORD=(& $random);JWT_SECRET=(& $random);MANAGER_USERNAME='actions-manager';MANAGER_PASSWORD=(& $random)} |
   ConvertTo-Json | Set-Content $credentialsPath -Encoding utf8
}
$credentials = Get-Content $credentialsPath -Raw | ConvertFrom-Json -AsHashtable
# Preserve existing credentials; apply only to this dedicated namespace. Never print values.
$secret = @{apiVersion='v1';kind='Secret';metadata=@{name='clinic-secrets';namespace=$ns};type='Opaque';data=@{}}
foreach ($key in $credentials.Keys) { $secret.data[$key] = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($credentials[$key])) }
$secretPath=Join-Path $script:Runtime 'secret.json'
$secret | ConvertTo-Json -Depth 8 | Set-Content $secretPath -Encoding utf8
try { Admin apply -f $secretPath | Out-Null } finally { Remove-Item -LiteralPath $secretPath }
$config = Admin config view --minify --flatten --raw -o json | ConvertFrom-Json
$tokenResult = Admin -n $ns create token clinic-actions-runner --duration=4h -o json | ConvertFrom-Json
$cluster = $config.clusters[0].cluster
$runnerConfig = @{apiVersion='v1';kind='Config';'current-context'='clinic-actions-demo';clusters=@(@{name='clinic-actions';cluster=$cluster});users=@(@{name='clinic-actions-runner';user=@{token=$tokenResult.status.token}});contexts=@(@{name='clinic-actions-demo';context=@{cluster='clinic-actions';user='clinic-actions-runner';namespace=$ns}})}
$kubeconfig=Join-Path $script:Runtime 'runner.kubeconfig'
$runnerConfig | ConvertTo-Json -Depth 20 | Set-Content $kubeconfig -Encoding utf8
$expiry = [DateTimeOffset]$tokenResult.status.expirationTimestamp
$expiry.ToUniversalTime().ToString('o', [Globalization.CultureInfo]::InvariantCulture) |
 Set-Content (Join-Path $script:Runtime 'token-expires.txt') -Encoding utf8
$nodes = (Admin get nodes -o json | ConvertFrom-Json).items.metadata.name
foreach ($node in $nodes) { Invoke-Checked docker @('inspect',$node,'--format','{{.Name}}') | Out-Null }
ConvertTo-Json -InputObject @($nodes) | Set-Content (Join-Path $script:Runtime 'nodes.json') -Encoding utf8
Write-Host "Provisioned isolated namespace. Runner token is short-lived; renew with this script before deployment. Credentials remain in $credentialsPath"
