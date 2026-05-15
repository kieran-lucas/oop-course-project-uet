# Business Rules

## Roles

| Role   | Permissions |
|--------|-------------|
| ADMIN  | Approve/reject deposit requests and password resets; force-cancel any auction |
| SELLER | Create items and auctions; cancel own auction before any bid is placed |
| BIDDER | Place manual bids; create auto-bid configurations |

- Roles are mutually exclusive and stored on `users.role`.
- Sellers and Admins **cannot** place bids.

---

## Bidding

### Manual Bid
- Only `BIDDER` accounts may call the manual-bid endpoint.
- Auction must be in `RUNNING` status.
- Bid amount must strictly exceed `auctions.current_price`.
- The current highest bidder (`auctions.leading_bidder_id`) **cannot** place another bid while they remain the leader.
- A bidder **cannot** bid on an auction for which they have an `ACTIVE` auto-bid config — they must deactivate it first.

### Seller Self-Bid
- The seller of an auction (`auctions.seller_id`) is blocked from bidding in that auction (enforced in `RunningState.placeBid()`).

### Anti-Sniping
- If a bid is placed with fewer than 30 seconds remaining, `auctions.end_time` is extended by 60 seconds and a `TIME_EXTENDED` WebSocket event is broadcast.

---

## Auto-Bid

Auto-bid lets a bidder set a maximum price; the system bids on their behalf automatically.

### Configuration fields
| Field              | Meaning |
|--------------------|---------|
| `max_bid`          | Maximum price the user is willing to pay |
| `increment_amount` | Step size for each automatic bid |
| `status`           | `ACTIVE` / `STOPPED` / `EXHAUSTED` / `FAILED` |
| `failure_reason`   | `MAX_PRICE_TOO_LOW` / `INSUFFICIENT_BALANCE` / `AUCTION_NOT_RUNNING` / `BIDDER_ALREADY_HIGHEST` / `ACTIVE_AUTOBID_EXISTS` |

### Rules
- Only one config per `(auction_id, bidder_id)` pair (UNIQUE constraint).
- Auto-bid can only be created while the auction is `RUNNING`.
- The bidder must **not** already be the current leader.
- An `ACTIVE_AUTOBID_EXISTS` failure is returned if a second config is attempted.
- On creation, an initial bid of `current_price + increment_amount` is placed immediately. If that amount exceeds `max_bid`, the config is created as `EXHAUSTED` without placing a bid. If the bidder's available balance is insufficient, the config is created as `FAILED`.
- The auto-bid chain runs within the **same transaction** as the triggering manual bid (max 100 auto-bids per chain to prevent infinite loops).
- Auto-bids are ordered by `registered_at` (FIFO) to ensure fairness.
- When an auto-bid would exceed `max_bid`, the config transitions to `EXHAUSTED` and the user is notified.
- When available balance is insufficient, the config transitions to `FAILED` and the user is notified.

---

## Auction Lifecycle

```
OPEN ──► RUNNING ──► SETTLING ──► PAID
                  └──────────────► FINISHED
          ▲
          └── CANCELED (only from OPEN, before any bid)
```

### Status reference

| Status     | Stored value | Plain-English meaning | Terminal? |
|------------|-------------|----------------------|-----------|
| `OPEN`     | `"OPEN"`    | **Created, not yet started.** `start_time` has not been reached. Bids are NOT accepted. Seller may still edit or cancel. | No |
| `RUNNING`  | `"RUNNING"` | **Actively accepting bids.** Scheduler transitions from `OPEN` once `start_time` ≤ now. | No |
| `SETTLING` | `"SETTLING"` | **Settlement in progress.** Scheduler claims this status atomically before processing payment. All client operations are blocked. Transient — resolved within the same scheduler tick. | No |
| `PAID`     | `"PAID"`    | **Successful outcome.** Winner's funds deducted, seller credited. The only status that represents a completed sale. | Yes |
| `FINISHED` | `"FINISHED"` | **Ended without a completed sale.** Two sub-cases: (1) auction expired with zero bids — no money moves; (2) winner existed but had insufficient funds at settlement time — reserved balance released, debt logged for admin review. | Yes |
| `CANCELED` | `"CANCELED"` | **Explicitly cancelled** by seller (only while `OPEN`) or by admin (any non-terminal status). All reserved balances released. | Yes |

> **Common confusion**
>
> - `OPEN` does **not** mean "open for bidding." It means the auction exists but `start_time` is in the future. Use `RUNNING` to check whether bids are accepted.
> - `FINISHED` does **not** mean "successfully finished." It is the failure/no-sale terminal state. A completed sale always ends in `PAID`.
> - The enum value in code is `CANCELED` (with a D), not `CANCEL`.

### Seller cancel rule
- A seller may cancel only while the auction is in `OPEN` status (i.e., before `start_time` and therefore before any bids).
- Admins may force-cancel at any non-terminal status (`OPEN`, `RUNNING`, or `SETTLING`).

### One auction per item
- An item can belong to at most one `OPEN` or `RUNNING` auction at a time (item status transitions to `IN_AUCTION` on auction creation, preventing a second auction).

---

## Wallet & Reserved Balance

Each user has two balance fields:

| Field              | Meaning |
|--------------------|---------|
| `balance`          | Total available funds |
| `reserved_balance` | Funds locked in active leading bids |

**Effective available balance = `balance − reserved_balance`**

Balance movements are recorded in the append-only `wallet_transactions` ledger:

| `kind`          | Trigger | Effect |
|-----------------|---------|--------|
| `DEPOSIT`       | Admin approves deposit request | `balance += amount` |
| `FREEZE`        | Bid placed and user becomes leader | `reserved_balance += amount` |
| `RELEASE`       | User is outbid | `reserved_balance -= old_amount` |
| `WIN_CONSUME`   | Settlement — winner pays | `balance -= amount`, `reserved_balance -= amount` |
| `SELLER_PAYOUT` | Settlement — seller receives | `(seller) balance += amount` |
| `CANCEL_RELEASE`| Auction cancelled | `reserved_balance -= amount` |

---

## Settlement

The scheduler runs every 5 seconds and settles auctions whose `end_time` has passed:

1. Auction is atomically moved to `SETTLING` to prevent concurrent modifications.
2. **No bids placed** → status becomes `FINISHED`; no money moves.
3. **Winner exists** → check winner's balance ≥ `current_price`:
   - **Sufficient**: deduct from winner (`WIN_CONSUME`), credit seller (`SELLER_PAYOUT`), status → `PAID`, notify winner.
   - **Insufficient**: release winner's reserved balance (`RELEASE`), status → `FINISHED` (debt scenario, handled by admin).

---

## Notifications

| Type                    | Trigger |
|-------------------------|---------|
| `OUTBID`                | User was outbid |
| `AUCTION_WON`           | User won a settled auction |
| `AUTOBID_FAILED`        | Auto-bid failed due to insufficient balance |
| `AUTOBID_EXHAUSTED`     | Auto-bid exhausted its maximum price |
| `DEPOSIT_APPROVED`      | Admin approved a deposit request |
| `PASSWORD_RESET_APPROVED` | Admin approved a password reset |

- Each notification has an `is_read` flag (default `false`).
- Users can mark a single notification or all notifications as read.
- Real-time delivery via WebSocket; database ensures offline users see history on reconnect.

---

## Bid History & Charts

- Every bid (manual and auto) is recorded as an immutable row in `bid_transactions`.
- `bid_transactions.auto_bid` distinguishes system-placed bids from user-initiated ones.
- The full history is retrievable via `BidService.getBidHistory(auctionId)` and used to render price-over-time charts in the UI.
- Records are never updated or deleted — they form an audit trail.
