$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    docker compose up -d
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose failed. Check Docker Desktop.' }

    docker run --rm --mount "type=bind,source=$($PWD.Path),target=/project" --mount "type=volume,target=/project/frontend/node_modules" -w /project/frontend node:22-bookworm sh -c "npm ci && npm run build"
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }

    $deadline = (Get-Date).AddMinutes(3)
    while ($true) {
        try {
            $issuer = Invoke-RestMethod 'http://localhost:8180/realms/trace/.well-known/openid-configuration' -TimeoutSec 4
            if ($issuer.issuer) { break }
        } catch {
            # Keycloak is still importing the local realm.
        }
        if ((Get-Date) -ge $deadline) { throw 'Keycloak startup timed out. Run docker compose logs keycloak.' }
        Start-Sleep -Seconds 2
    }

    Set-Location backend
    .\gradlew.bat --console=plain bootRun
    if ($LASTEXITCODE -ne 0) { throw 'Spring Boot exited with an error.' }
} finally {
    Pop-Location
}
