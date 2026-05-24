$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$readmePath = Join-Path $repoRoot "README.md"

if (!(Test-Path $readmePath)) {
  throw "README.md not found at $readmePath"
}

$content = Get-Content -Raw -Encoding UTF8 $readmePath

function Count-Occurrences {
  param(
    [string] $Text,
    [string] $Needle
  )
  return ([regex]::Matches($Text, [regex]::Escape($Needle))).Count
}

function Replace-Required {
  param(
    [string] $OldBlock,
    [string] $NewBlock,
    [string] $Label,
    [int] $MinimumCount = 1
  )

  $count = Count-Occurrences -Text $script:content -Needle $OldBlock
  if ($count -lt $MinimumCount) {
    throw "Expected at least $MinimumCount occurrence(s) for '$Label', found $count. README may have changed; inspect manually before applying."
  }

  $script:content = $script:content.Replace($OldBlock, $NewBlock)
  Write-Host "Updated $Label ($count occurrence(s))."
}

$oldBidHistoryEntry = @'
    class BidHistoryEntry {
        <<record>>
        -id
        -auctionId
        -bidderId
        -bidderUsername
        -amount
        -autoBid
        -createdAt
    }
'@

$newBidHistoryEntry = @'
    class BidHistoryEntry {
        <<record>>
        -transaction
        -username
        +getAuctionId()
        +getBidderId()
        +getAmount()
        +isAutoBid()
        +getCreatedAt()
    }
'@

$oldBidUpdateConstants = @'
    class BidUpdateMessage {
        -TYPE_BID_UPDATE
        -TYPE_TIME_EXTENDED
        -TYPE_AUCTION_ENDED
        -TYPE_AUTO_BID_TRIGGERED
        -TYPE_BALANCE_UPDATED
        -TYPE_USER_NOTIFICATION
'@

$newBidUpdateConstants = @'
    class BidUpdateMessage {
        +TYPE_BID_UPDATE
        +TYPE_TIME_EXTENDED
        +TYPE_AUCTION_ENDED
        +TYPE_AUTO_BID_TRIGGERED
        +TYPE_BALANCE_UPDATED
        +TYPE_USER_NOTIFICATION
'@

$oldBalanceDisplay = @'
    class BalanceDisplay {
        <<record>>
        -balance
        -availableBalance
    }
'@

$newBalanceDisplay = @'
    class BalanceDisplay {
        <<record>>
        -text
        -color
    }
'@

$oldGlassHelpers = @'
    class GlassDateCell {
        <<nested class>>
        -picker
        -state
        +updateItem()
    }

    class GlassCalendarState {
        <<record>>
        -visibleMonth
        -selectedDate
    }
'@

$newGlassHelpers = @'
    class GlassDateCell {
        <<nested class>>
        -picker
        -state
        -shadow
        -GlassDateCell()
        +updateItem()
        -refreshAppearance()
    }

    class GlassCalendarState {
        <<nested class>>
        -hoveredCell
        -hoverProgress
        -hoverTimeline
        -refreshAll()
    }
'@

Replace-Required -OldBlock $oldBidHistoryEntry -NewBlock $newBidHistoryEntry -Label "BidHistoryEntry record shape" -MinimumCount 2
Replace-Required -OldBlock $oldBidUpdateConstants -NewBlock $newBidUpdateConstants -Label "BidUpdateMessage public TYPE constants" -MinimumCount 1
Replace-Required -OldBlock $oldBalanceDisplay -NewBlock $newBalanceDisplay -Label "BalanceDisplay record components" -MinimumCount 1
Replace-Required -OldBlock $oldGlassHelpers -NewBlock $newGlassHelpers -Label "GlassDateCell and GlassCalendarState nested helpers" -MinimumCount 1

Set-Content -Path $readmePath -Value $content -Encoding UTF8 -NoNewline

Write-Host "README UML strict residual fixes applied successfully."
Write-Host "Review with: git diff -- README.md"
