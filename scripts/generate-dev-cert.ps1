# Generates a locally-trusted HTTPS certificate for the dev server (server.ssl.* in
# application-dev.properties) using mkcert, so https://localhost:8080 shows no browser
# warning and real cross-origin fetch() calls from the Angular dev server work.
# Usage: .\scripts\generate-dev-cert.ps1
# One-time prerequisite: scoop install mkcert (bucket: extras) - or see https://github.com/FiloSottile/mkcert

if (-not (Get-Command mkcert -ErrorAction SilentlyContinue)) {
    Write-Error "mkcert not found. Install it first: scoop bucket add extras; scoop install mkcert"
    exit 1
}

# Installs (or confirms) mkcert's local CA in the system/browser trust stores. Idempotent.
mkcert -install

$certsDir = Join-Path $PSScriptRoot "..\certs"
if (-not (Test-Path $certsDir)) {
    New-Item -ItemType Directory -Path $certsDir | Out-Null
}

$p12Path = Join-Path $certsDir "dev-keystore.p12"

# mkcert hardcodes "changeit" as the PKCS#12 password - not configurable, and fine for a
# throwaway local-only keystore (see server.ssl.key-store-password in application-dev.properties).
mkcert -pkcs12 -p12-file $p12Path localhost 127.0.0.1 ::1

Write-Host "Dev HTTPS keystore generated at $p12Path"
Write-Host "Start the app with the dev profile and it will serve https://localhost:8080"
