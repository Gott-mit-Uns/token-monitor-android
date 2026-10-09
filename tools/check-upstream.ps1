[CmdletBinding()]
param()
# Compatibility entry point; one implementation is maintained on all platforms.
$ErrorActionPreference = 'Stop'
& node (Join-Path $PSScriptRoot 'check-upstream.mjs')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
