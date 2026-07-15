# Starts the Spring Boot app without going through `mvnw spring-boot:run`.
#
# Background: this project's path contains Japanese characters (OneDrive\Desktop),
# and `mvnw spring-boot:run` passes the classpath to java via a temporary @argfile.
# On this environment that mechanism fails with
# `ClassNotFoundException: com.example.ec.EcApplication`.
# This script avoids the @argfile entirely by (1) compiling, (2) resolving the
# dependency classpath, and (3) passing -cp directly to java.
#
# Usage: run `.\run.ps1` from the project root (Ctrl+C to stop).

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

Write-Host "[1/3] Compiling..."
& .\mvnw.cmd -q -DskipTests compile
if ($LASTEXITCODE -ne 0) {
    throw "Compile failed - check the Maven output above"
}

Write-Host "[2/3] Resolving dependency classpath..."
$cpFile = Join-Path $env:TEMP "ec-site-classpath.txt"
& .\mvnw.cmd -q dependency:build-classpath "-Dmdep.outputFile=$cpFile"
if ($LASTEXITCODE -ne 0) {
    throw "Classpath resolution failed - check the Maven output above"
}

$dependencyClasspath = Get-Content $cpFile -Raw
$classpath = "target\classes;$dependencyClasspath"

Write-Host "[3/3] Starting application (Ctrl+C to stop)..."
& java -cp $classpath com.example.ec.EcApplication
