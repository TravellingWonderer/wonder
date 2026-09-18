# Smoke-test Duffel Flights + Stays with a test token.
# Usage:
#   $env:DUFFEL_TOKEN = "duffel_test_..."
#   powershell -File scripts/duffel-smoke.ps1
#
# Or: powershell -File scripts/duffel-smoke.ps1 -Token "duffel_test_..."

param(
    [string]$Token = $env:DUFFEL_TOKEN
)

if ([string]::IsNullOrWhiteSpace($Token)) {
    Write-Host "No token. Create one at https://app.duffel.com → Developers → Access tokens (test mode)."
    Write-Host "Then: `$env:DUFFEL_TOKEN='duffel_test_...' ; .\scripts\duffel-smoke.ps1"
    exit 1
}

$headers = @{
    "Authorization"  = "Bearer $Token"
    "Duffel-Version" = "v2"
    "Accept"         = "application/json"
    "Content-Type"   = "application/json"
}

$depart = (Get-Date).AddDays(21).ToString("yyyy-MM-dd")
$ret    = (Get-Date).AddDays(25).ToString("yyyy-MM-dd")

$flightBody = @{
    data = @{
        slices = @(
            @{ origin = "LHR"; destination = "LIS"; departure_date = $depart },
            @{ origin = "LIS"; destination = "LHR"; departure_date = $ret }
        )
        passengers = @(@{ type = "adult" })
        cabin_class = "economy"
    }
} | ConvertTo-Json -Depth 6

Write-Host "→ Offer request LHR↔LIS $depart / $ret ..."
try {
    $flight = Invoke-RestMethod `
        -Method Post `
        -Uri "https://api.duffel.com/air/offer_requests?return_offers=true&supplier_timeout=20000" `
        -Headers $headers `
        -Body $flightBody
    $offers = @($flight.data.offers)
    Write-Host "  OK — $($offers.Count) offers. Cheapest: $($offers[0].total_amount) $($offers[0].total_currency)"
} catch {
    Write-Host "  FAIL flights: $($_.Exception.Message)"
    if ($_.ErrorDetails.Message) { Write-Host "  $($_.ErrorDetails.Message)" }
}

$stayBody = @{
    data = @{
        location = @{
            radius = 5
            geographic_coordinates = @{ latitude = 38.7223; longitude = -9.1393 }
        }
        check_in_date  = $depart
        check_out_date = $ret
        guests         = @(@{ type = "adult" })
        rooms          = 1
    }
} | ConvertTo-Json -Depth 6

Write-Host "→ Stays search Lisbon ..."
try {
    $stays = Invoke-RestMethod `
        -Method Post `
        -Uri "https://api.duffel.com/stays/search" `
        -Headers $headers `
        -Body $stayBody
    $results = @($stays.data.results)
    if ($results.Count -eq 0) {
        Write-Host "  OK response but 0 results (or Stays not enabled for this org)."
    } else {
        Write-Host "  OK — $($results.Count) stays. Cheapest: $($results[0].cheapest_rate_total_amount) $($results[0].cheapest_rate_currency)"
    }
} catch {
    Write-Host "  Stays unavailable (app will use approximate stay prices): $($_.Exception.Message)"
    if ($_.ErrorDetails.Message) { Write-Host "  $($_.ErrorDetails.Message)" }
}

Write-Host "Done."
