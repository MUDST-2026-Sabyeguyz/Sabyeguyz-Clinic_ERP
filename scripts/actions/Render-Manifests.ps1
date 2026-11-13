[CmdletBinding()]
param([Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{40}$')][string]$SourceSha,
 [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{12}-[0-9]+-[0-9]+$')][string]$ImageTag,
 [Parameter(Mandatory)][string]$OutputDirectory)
. "$PSScriptRoot/Common.ps1"
New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
$ns=$script:Namespace
$config=@{apiVersion='v1';kind='ConfigMap';metadata=@{name='clinic-config';namespace=$ns};data=@{
 POSTGRES_DB='clinic_actions_demo';POSTGRES_USER='clinic';SPRING_PROFILES_ACTIVE='docker,v2';
 SPRING_DATASOURCE_URL='jdbc:postgresql://postgres:5432/clinic_actions_demo';SPRING_DATASOURCE_USERNAME='clinic';
 SPRING_FLYWAY_ENABLED='false';JAVA_TOOL_OPTIONS='-XX:MaxRAMPercentage=65.0'}}
$envFrom=@(@{configMapRef=@{name='clinic-config'}})
$dbEnv=@(@{name='SPRING_DATASOURCE_PASSWORD';valueFrom=@{secretKeyRef=@{name='clinic-secrets';key='POSTGRES_PASSWORD'}}})
$appEnv=$dbEnv+@(@{name='JWT_SECRET';valueFrom=@{secretKeyRef=@{name='clinic-secrets';key='JWT_SECRET'}}},
 @{name='CLINIC_BOOTSTRAP_MANAGER_USERNAME';valueFrom=@{secretKeyRef=@{name='clinic-secrets';key='MANAGER_USERNAME'}}},
 @{name='CLINIC_BOOTSTRAP_MANAGER_PASSWORD';valueFrom=@{secretKeyRef=@{name='clinic-secrets';key='MANAGER_PASSWORD'}}})
function Deployment($name,$image,$port,$environment,$from,$volume=$null) {
 $container=@{name=$name;image=$image;imagePullPolicy='Never';ports=@(@{containerPort=$port});
 resources=@{requests=@{cpu='100m';memory='128Mi'};limits=@{cpu='2';memory='1Gi'}}}
 if ($environment) { $container.env=$environment }; if ($from) { $container.envFrom=$from }
 if ($name -eq 'postgres') {
  $container.env=@(@{name='POSTGRES_PASSWORD';valueFrom=@{secretKeyRef=@{name='clinic-secrets';key='POSTGRES_PASSWORD'}}},@{name='PGDATA';value='/var/lib/postgresql/data/pgdata'})
  $container.volumeMounts=@(@{name='data';mountPath='/var/lib/postgresql/data'})
  $container.readinessProbe=@{exec=@{command=@('pg_isready','-U','clinic','-d','clinic_actions_demo')};periodSeconds=5}
 } else {
  $path=if($name -eq 'backend'){'/actuator/health'}else{'/healthz'}
  $container.startupProbe=@{httpGet=@{path=$path;port=$port};periodSeconds=5;failureThreshold=60}
  $container.readinessProbe=@{httpGet=@{path=$path;port=$port};periodSeconds=5}
 }
 $pod=@{automountServiceAccountToken=$false;containers=@($container)}
 if($volume) { $pod.volumes=@(@{name='data';persistentVolumeClaim=@{claimName='postgres-data'}}) }
 @{apiVersion='apps/v1';kind='Deployment';metadata=@{name=$name;namespace=$ns;annotations=@{'clinic.source-sha'=$SourceSha}};spec=@{replicas=1;revisionHistoryLimit=3;strategy=@{type='Recreate'};selector=@{matchLabels=@{app=$name}};template=@{metadata=@{labels=@{app=$name};annotations=@{'clinic.source-sha'=$SourceSha}};spec=$pod}}}
}
function Service($name,$port) { @{apiVersion='v1';kind='Service';metadata=@{name=$name;namespace=$ns};spec=@{selector=@{app=$name};ports=@(@{port=$port;targetPort=$port})}} }
$database=@($config,(Deployment 'postgres' 'postgres:16-alpine' 5432 $null $envFrom $true),(Service 'postgres' 5432))
$app=@((Deployment 'backend' "clinic-actions-backend:$ImageTag" 8080 $appEnv $envFrom),(Service 'backend' 8080),
 (Deployment 'frontend' "clinic-actions-frontend:$ImageTag" 80 $null $null),(Service 'frontend' 80))
$job=@{apiVersion='batch/v1';kind='Job';metadata=@{name="migrate-$ImageTag";namespace=$ns};spec=@{backoffLimit=0;activeDeadlineSeconds=300;ttlSecondsAfterFinished=86400;template=@{spec=@{restartPolicy='Never';automountServiceAccountToken=$false;containers=@(@{name='migrate';image="clinic-actions-backend:$ImageTag";imagePullPolicy='Never';command=@('java','-Dloader.main=com.sabyeguyz.clinicv2.config.ActionsMigrationMain','-cp','/app/app.jar','org.springframework.boot.loader.launch.PropertiesLauncher');envFrom=$envFrom;env=$dbEnv;resources=@{requests=@{cpu='100m';memory='128Mi'};limits=@{cpu='1';memory='512Mi'}}})}}}}
@{apiVersion='v1';kind='List';items=$database} | ConvertTo-Json -Depth 35 | Set-Content (Join-Path $OutputDirectory 'database.json') -Encoding utf8
@{apiVersion='v1';kind='List';items=$app} | ConvertTo-Json -Depth 35 | Set-Content (Join-Path $OutputDirectory 'application.json') -Encoding utf8
$job | ConvertTo-Json -Depth 35 | Set-Content (Join-Path $OutputDirectory 'migration.json') -Encoding utf8
