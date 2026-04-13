# Run Symfony bin/console with PHP on PATH (fixes Cursor terminal).
# Example: .\console.ps1 app:create-staff-user admin@club.ru "YourPassword" --name="Admin"

param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $ConsoleArgs
)

. (Join-Path $PSScriptRoot '_prepend-winget-php.ps1')

if (-not $CrmPhpExe -or -not (Test-Path -LiteralPath $CrmPhpExe)) {
    Write-Error 'PHP not found. Install: winget install PHP.PHP.8.5'
    exit 1
}

Set-Location $PSScriptRoot
$console = Join-Path $PSScriptRoot 'bin\console'
$dExt = @()
if ($CrmPhpExtDir) {
    $dExt = @('-d', "extension_dir=$CrmPhpExtDir")
}
if ($CrmPhpIni) {
    if ($null -eq $ConsoleArgs -or $ConsoleArgs.Count -eq 0) {
        & $CrmPhpExe -c $CrmPhpIni @dExt $console
    } else {
        & $CrmPhpExe -c $CrmPhpIni @dExt $console @ConsoleArgs
    }
} else {
    if ($null -eq $ConsoleArgs -or $ConsoleArgs.Count -eq 0) {
        & $CrmPhpExe @dExt $console
    } else {
        & $CrmPhpExe @dExt $console @ConsoleArgs
    }
}
exit $LASTEXITCODE
