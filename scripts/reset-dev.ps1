# Resets the local dev database: wipes every table (schema and Flyway history are kept).
# Runs the app once with the "dev" and "seed" profiles as a one-shot job, then it exits by itself.
# Refuses to run unless the database URLs point at localhost.
# Usage: .\scripts\reset-dev.ps1

$projectRoot = Join-Path $PSScriptRoot ".."

Push-Location $projectRoot
try {
    & .\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=dev,seed"
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Dev data reset failed (exit code $LASTEXITCODE)"
        exit $LASTEXITCODE
    }
    Write-Host "Dev database reset done"
}
finally {
    Pop-Location
}
