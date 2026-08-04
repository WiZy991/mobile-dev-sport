# Build FitnessClubAgent.exe (Windows)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

function Resolve-Python {
    $candidates = @()
    if (Get-Command py -ErrorAction SilentlyContinue) {
        $candidates += @{ Cmd = "py"; Args = @("-3") }
    }
    if (Get-Command python -ErrorAction SilentlyContinue) {
        $candidates += @{ Cmd = "python"; Args = @() }
    }
    if (Get-Command python3 -ErrorAction SilentlyContinue) {
        $candidates += @{ Cmd = "python3"; Args = @() }
    }

    foreach ($c in $candidates) {
        try {
            $out = & $c.Cmd @($c.Args + @("-c", "import sys; print(sys.executable)")) 2>&1
            if ($LASTEXITCODE -ne 0) { continue }
            $exe = ($out | Select-Object -Last 1).ToString().Trim()
            if ($exe -and (Test-Path -LiteralPath $exe)) {
                Write-Host "Using Python: $exe" -ForegroundColor Gray
                return $exe
            }
        } catch {
            continue
        }
    }

    throw "Python not found. Install from python.org and check Add python.exe to PATH, then reopen the terminal."
}

$python = Resolve-Python

Write-Host "Installing dependencies..." -ForegroundColor Cyan
& $python -m pip install -q -r requirements.txt pyinstaller
if ($LASTEXITCODE -ne 0) {
    throw "pip install failed (exit $LASTEXITCODE)."
}

if (Test-Path -LiteralPath "dist") { Remove-Item -Recurse -Force "dist" }
if (Test-Path -LiteralPath "build") { Remove-Item -Recurse -Force "build" }

Write-Host "Running PyInstaller..." -ForegroundColor Cyan
& $python -m PyInstaller --noconfirm --clean "club_agent.spec"
if ($LASTEXITCODE -ne 0) {
    throw "PyInstaller failed (exit $LASTEXITCODE). Scroll up for the real error."
}

$outExe = Join-Path $PSScriptRoot (Join-Path "dist" "FitnessClubAgent.exe")
# Brief retry: antivirus sometimes locks/deletes the file for a moment.
$found = $false
for ($i = 0; $i -lt 10; $i++) {
    if (Test-Path -LiteralPath $outExe) {
        $found = $true
        break
    }
    Start-Sleep -Milliseconds 300
}

if (-not $found) {
    $listing = ""
    $distDir = Join-Path $PSScriptRoot "dist"
    if (Test-Path -LiteralPath $distDir) {
        $listing = (Get-ChildItem -LiteralPath $distDir -Recurse -ErrorAction SilentlyContinue |
            ForEach-Object { $_.FullName }) -join "`n"
    }
    throw ("Build finished but FitnessClubAgent.exe not found at:`n{0}`nDist contents:`n{1}" -f $outExe, $listing)
}

Write-Host ""
Write-Host "Done: $outExe" -ForegroundColor Green
