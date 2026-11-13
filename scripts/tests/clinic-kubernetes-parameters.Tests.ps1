$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '..\clinic-kubernetes-parameters.ps1')

Describe 'Clinic Kubernetes deployment parameters' {
    It 'accepts an isolated local demo database name' {
        { Assert-ClinicDeploymentParameters -DatabaseName 'clinic_demo_review' -DatabaseKind local-demo -SeedMode full } | Should Not Throw
    }

    It 'accepts a non-demo default database without enabling seeds' {
        { Assert-ClinicDeploymentParameters -DatabaseName 'clinic_demo' -DatabaseKind unspecified -SeedMode none } | Should Not Throw
    }

    It 'rejects unsafe PostgreSQL database identifiers' {
        $threw = $false
        try { Assert-ClinicDeploymentParameters -DatabaseName 'Clinic-Review' -DatabaseKind local-demo -SeedMode full }
        catch { $threw = $true }
        $threw | Should Be $true
    }

    It 'rejects demo seed when the database kind is unspecified' {
        $threw = $false
        try { Assert-ClinicDeploymentParameters -DatabaseName 'clinic_demo_review' -DatabaseKind unspecified -SeedMode full }
        catch { $threw = $true }
        $threw | Should Be $true
    }
}
