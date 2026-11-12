function Assert-ClinicDeploymentParameters {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$DatabaseName,
        [Parameter(Mandatory)][ValidateSet('unspecified','local-demo')][string]$DatabaseKind,
        [Parameter(Mandatory)][ValidateSet('none','minimal','full')][string]$SeedMode
    )

    if ($DatabaseName -notmatch '^[a-z][a-z0-9_]{0,62}$') {
        throw 'DatabaseName must be a lowercase PostgreSQL identifier.'
    }
    if ($SeedMode -ne 'none' -and $DatabaseKind -ne 'local-demo') {
        throw 'Demo seed requires -DatabaseKind local-demo.'
    }
}
