# Loads KEY=VALUE pairs from .env into the current PowerShell session's environment variables.
# Usage: . .\scripts\load-env.ps1   (dot-source it, so the variables persist in your shell)

$envFile = Join-Path $PSScriptRoot "..\.env"

if (-not (Test-Path $envFile)) {
    Write-Warning ".env not found at $envFile - copy .env.example to .env and fill in real values first."
    return
}

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) {
        return
    }
    if ($line -match '^([^=]+)=(.*)$') {
        $name = $matches[1].Trim()
        $value = $matches[2].Trim()
        [System.Environment]::SetEnvironmentVariable($name, $value)
    }
}

Write-Host "Loaded environment variables from .env"
