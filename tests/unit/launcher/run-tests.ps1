$ErrorActionPreference = 'Stop'
$launcherRoot = Join-Path $PSScriptRoot '../../../clients/launcher'
$source = "#nullable enable`n" + (Get-Content -LiteralPath (Join-Path $launcherRoot 'DNFLogin/LoginConfig.cs') -Raw)
$tests = "#nullable enable`n" + (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'LauncherConfigTests.cs') -Raw)
$references = @(Get-ChildItem -LiteralPath (Join-Path $PSHOME 'ref') -Filter '*.dll' | ForEach-Object FullName)
$references += Join-Path $PSHOME 'System.Security.Cryptography.ProtectedData.dll'
# Compile both files together so the internal production configuration class is tested directly.
$testRoot = [IO.Path]::GetFullPath((Join-Path $launcherRoot '.build-tools'))
$testDirectory = Join-Path $testRoot ('dof86-launcher-tests-' + [Guid]::NewGuid().ToString('N'))
try {
    # A separate namespace-scoped source permits independent using directives in both inputs.
    $combined = $source + "`nnamespace LauncherSafetyTests {`n" + $tests.Replace('#nullable enable', '') + "`n}"
    Add-Type -TypeDefinition $combined -ReferencedAssemblies $references
    $count = [LauncherSafetyTests.LauncherConfigTests]::Run($testDirectory)
    Write-Output "PASS: $count launcher configuration and Windows DPAPI checks"
}
finally {
    if (Test-Path -LiteralPath $testDirectory) {
        $resolved = [IO.Path]::GetFullPath($testDirectory)
        if (!$resolved.StartsWith($testRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or !(Split-Path $resolved -Leaf).StartsWith('dof86-launcher-tests-')) { throw 'Unexpected test cleanup path' }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
