# Business Rules

This document describes the business behavior implemented by the current Java source code. It is written for reviewers who need to verify what the system allows, rejects, and records.

Runtime path:

```text
JavaFX client -> Javalin controller -> service validation -> JDBI transaction -> PostgreSQL tables
```

Client-side checks improve usability, but server-side services and DAOs are the source of truth.

---

## 1. Roles and Access Boundaries

| Role | Main permissions | Important limitations |
|---|---|---|
| `ADMIN` | Approve/reject deposits; approve/reject password-reset requests; list/delete users when safe; soft-cancel or hard-delete auctions through admin routes | Not created through public registration; seeded by `AdminSeeder` |
| `SELLER` | Create/edit/delete own available items; create/edit own auctions; cancel own auction only when state rules allow it | Cannot place bids; cannot bid on own auction |
| `BIDDER` | Request deposits; place manual bids; configure/stop own auto-bids; read notifications | Cannot create items or auctions; cannot use admin workflows |

Rules:

- Roles are stored in `users.role` as `ADMIN`, `SELLER`, or `BIDDER`.
- Public registration creates normal application users; admin creation is controlled by startup seeding.
- Protected routes use JWT middleware and then apply controller/service role checks.
- The JavaFX client hides screens by role, but backend authorization remains mandatory.

---

## 2. Authentication and Session Rules

| Rule | Source-level behavior |
|---|---|
| Password storage | Passwords are stored as BCrypt hashes in `users.password_hash`. |
| JWT claims | Tokens carry user identity, role, and token version. |
| Required secret | Server startup validates `JWT_SECRET`; it must be present and at least 32 UTF-8 bytes. |
| Token invalidation | `users.token_version` is checked by middleware and incremented after password changes/resets. |
| Email normalization | Public registration stores email as trimmed lowercase so reset lookup and duplicate checks are consistent. |

Seeded admin behavior:

- Startup creates the default admin only when no admin exists.
- The classroom/demo fallback password is not a production security model.

---

## 3. Item Rules

| Rule | Meaning |
|---|---|
| Ownership | Every item belongs to one seller through `items.seller_id`. |
| Owner control | A seller can edit/delete only their own items. |
| Mutation status guard | Item update/delete is allowed only while the item status is `AVAILABLE`. |
| Categories | Supported categories are `ELECTRONICS`, `ART`, and `VEHICLE`. |
| Category details | Electronics/Vehicle use `brand`; Art uses `artist`; `year` is optional. |
| Auction eligibility | A new auction requires an item that is currently `AVAILABLE`. |

Item status values: `AVAILABLE`, `IN_AUCTION`, `SOLD`, `REMOVED`.

Item lifecycle synchronization:

| Event | Item status after event |
|---|---|
| Auction is created successfully | `IN_AUCTION` |
| Auction is paid successfully | `SOLD` |
| Auction finishes with no bids | `AVAILABLE` |
| Auction settlement fails because winner cannot pay | `AVAILABLE` |
| Auction is soft-cancelled before completion | `AVAILABLE` |
| Admin hard-deletes an unpaid/cancelled auction | `AVAILABLE` |
| Admin hard-deletes a paid auction | `SOLD` |

This prevents stale `IN_AUCTION` items from blocking future valid auctions after cancellation or failed settlement.

---

## 4. Auction Lifecycle

```text
OPEN -> RUNNING -> SETTLING -> PAID
                 -> FINISHED

OPEN    -> CANCELED
RUNNING -> CANCELED
```

| Status | Meaning |
|---|---|
| `OPEN` | Created but not yet accepting bids. |
| `RUNNING` | Accepts valid bids. |
| `SETTLING` | Scheduler has claimed the expired auction for settlement. |
| `PAID` | Winner payment and seller payout completed. |
| `FINISHED` | Auction ended without completed sale, for example no bids or failed settlement. |
| `CANCELED` | Soft-cancelled before normal completion. |

Important distinctions:

- `OPEN` does not mean open for bidding. Bids require `RUNNING`.
- `FINISHED` does not mean successful payment. Successful sale uses `PAID`.
- `SETTLING` prevents duplicate scheduler settlement.
- Admin hard-delete is cleanup, not a lifecycle status.

Cancel/delete rules:

- Seller can cancel only their own `OPEN` auction.
- Seller cannot cancel a `RUNNING` auction.
- Admin can soft-cancel `OPEN` or `RUNNING` auctions.
- If a running auction with a leader is cancelled, the leader reservation is released and `CANCEL_RELEASE` is recorded in the wallet ledger.
- Soft-cancelled auctions restore their item to `AVAILABLE`.
- Soft-cancel and settlement stop active auto-bid configs for the auction in the same transaction.
- Admin hard-delete removes dependent wallet, auto-bid, and bid rows before deleting the auction row, while keeping item status consistent with the auction outcome.

---

## 5. Auction Creation and Update Rules

| Rule | Behavior |
|---|---|
| Seller ownership | The selected item must belong to the seller creating the auction. |
| Item availability | The item must be `AVAILABLE`. |
| Starting price | Must be positive. |
| Time range | `end_time` must be after `start_time`. |
| Update time range | Partial auction updates are validated against the final merged `start_time`/`end_time` pair. |
| Active conflict prevention | The same item cannot have another active auction. |
| Paid-item protection | An item already associated with a paid auction is not auctioned again. |

On successful creation:

- `auctions.current_price` starts from `starting_price`.
- `auctions.status` starts as `OPEN`.
- Item status is moved to `IN_AUCTION`.

---

## 6. Manual Bidding Rules

A manual bid is accepted only when all conditions below are true:

| Condition | Requirement |
|---|---|
| Caller role | Caller must be `BIDDER`. |
| Auction state | Auction must be `RUNNING`. |
| Seller self-bid | Auction seller cannot bid on their own auction. |
| Amount | Bid amount must be positive and greater than `auctions.current_price`. |
| Current leader | Current leading bidder cannot bid again while still leading. |
| Auto-bid conflict | A bidder with active auto-bid for the same auction must stop it before manual bidding. |
| Available balance | Bidder must have enough `balance - reserved_balance`. |

Successful leading bid behavior:

1. The auction row and relevant user rows are locked inside a JDBI transaction.
2. Previous leader reservation is released.
3. New leader amount is added to `reserved_balance`.
4. A `bid_transactions` row is inserted.
5. Wallet movement is recorded in `wallet_transactions`.
6. WebSocket updates are sent after transaction commit.

---

## 7. Anti-Sniping Rule

- If a valid bid arrives with fewer than 30 seconds remaining, the auction end time is extended by 60 seconds.
- A `TIME_EXTENDED` WebSocket message is sent after commit.
- The rule applies to manual and auto-bid paths when they produce a valid leading bid.

---

## 8. Auto-Bid Rules

Auto-bid lets a bidder define a maximum bid and increment amount. The strategy can bid for that user while rules and balance allow it.

| Field | Meaning |
|---|---|
| `max_bid` | Highest price the bidder accepts. |
| `increment_amount` | Step used for automatic bids. |
| `active` | Legacy compatibility flag. |
| `status` | `ACTIVE`, `STOPPED`, `EXHAUSTED`, or `FAILED`. |
| `failure_reason` | Stored reason when a config cannot continue. |
| `registered_at` | FIFO ordering timestamp for deterministic chain order. |

Creation rules:

- Auction must be `RUNNING`.
- Bidder must not already be current leader.
- A bidder can have only one config per auction because of the unique database constraint.
- A second active config for the same auction is rejected.
- Initial bid is `current_price + increment_amount`.
- If initial bid exceeds `max_bid`, config becomes `EXHAUSTED` with `MAX_PRICE_TOO_LOW`.
- If available balance is insufficient, config becomes `FAILED` with `INSUFFICIENT_BALANCE`.
- If valid, the initial auto-bid is placed immediately and reserved.

Chain rules:

- Chain execution runs in the same transaction as the triggering bid path.
- The chain is capped at 100 auto-bids per trigger.
- Active configs are processed by `registered_at`.
- Configs unable to exceed current price become `EXHAUSTED`; configs lacking balance become `FAILED`.
- Active configs are moved to `STOPPED` when the auction is cancelled or settled.

---

## 9. Wallet, Reservation, and Ledger Rules

| Field | Meaning |
|---|---|
| `balance` | Total wallet balance recorded for the user. |
| `reserved_balance` | Amount locked by current leading bids. |

Available balance:

```text
available_balance = balance - reserved_balance
```

| Ledger `kind` | Trigger | Source-level meaning |
|---|---|---|
| `DEPOSIT` | Admin approves a deposit request | User balance increases. |
| `FREEZE` | User becomes leading bidder | User reserved balance increases. |
| `RELEASE` | User is outbid or settlement cannot charge winner | User reserved balance decreases. |
| `WIN_CONSUME` | Winner successfully pays during settlement | Winner balance and reserved balance are consumed according to service logic. |
| `SELLER_PAYOUT` | Seller receives proceeds | Seller balance increases. |
| `CANCEL_RELEASE` | Running auction is cancelled with a leader | Leader reserved balance decreases. |

Ledger intent:

- Money movements are append-only and auditable during normal business operations.
- `reserved_balance` prevents one wallet from overcommitting to multiple leading bids.
- Transactional service code keeps wallet, auction, item status, and notification changes consistent.
- Admin hard-delete is explicit destructive cleanup and may remove auction-scoped ledger rows together with the auction's dependent rows.

---

## 10. Deposit Workflow

| Step | Rule |
|---|---|
| Request | User creates a `PENDING` deposit request; balance is unchanged. |
| Approve | Admin adds amount to balance, writes a `DEPOSIT` ledger row, and marks request `APPROVED`. |
| Reject | Admin marks request `REJECTED`; balance is unchanged. |
| Reprocess guard | A non-`PENDING` request cannot be processed again. |

---

## 11. Password Reset Workflow

| Step | Rule |
|---|---|
| Request | User creates a pending reset request if no pending request already exists. |
| Approve | Admin generates a temporary password, stores a new BCrypt hash, increments token version, and receives the temporary password in response. |
| Reject | Admin rejects the request without changing the password. |
| Pending uniqueness | The database enforces at most one pending reset request per user. |

This is an admin-reviewed classroom flow. Production should replace it with secure out-of-band reset delivery.

---

## 12. Settlement Rules

The scheduler processes expired `RUNNING` auctions:

1. Claim a due auction by moving `RUNNING` to `SETTLING` atomically.
2. If there is no leading bidder, mark the auction `FINISHED` and restore the item to `AVAILABLE`.
3. If the winner can pay, record winner consumption, record seller payout, mark the auction `PAID`, and mark the item `SOLD`.
4. If the winner cannot pay, release the reservation, mark the auction `FINISHED`, and restore the item to `AVAILABLE`.
5. Stop active auto-bid configs for the auction in the same transaction.
6. Insert result notifications in the same transaction and push realtime messages after commit.

---

## 13. Notifications and Realtime Events

| Type / event | Trigger |
|---|---|
| `OUTBID` | Previous leader is replaced. |
| `SELLER_BID_RECEIVED` | Seller receives a manual or auto bid. |
| `AUTOBID_FAILED` | Auto-bid cannot activate or continue due to balance. |
| `AUTOBID_EXHAUSTED` | Auto-bid cannot continue because next bid exceeds `max_bid`. |
| `AUCTION_RESULT` | Auction final result is available. |
| `AUCTION_WON` | Winner successfully pays. |
| `SELLER_PAYOUT` | Seller receives payout. |
| `AUCTION_CANCELED` | Auction is cancelled. |
| `AUCTION_DELETED` | Auction is removed by admin cleanup. |
| `BALANCE_UPDATED` | Deposit or balance state changes. |
| `BID_UPDATE` | Auction price/leader changes. |
| `TIME_EXTENDED` | Anti-sniping extension happens. |
| `AUCTION_ENDED` | Auction ending event is broadcast. |

- Persistent notifications are stored in `notifications`.
- Auction updates use `/ws/auction/{id}`.
- User-specific notifications use `/ws/user/{id}`.

---

## 14. Bid History and Charts

- Accepted manual and auto bids insert rows into `bid_transactions`.
- `auto_bid` distinguishes automatic bids from manual bids.
- Bid history powers the JavaFX auction-detail price chart.
- Normal bidding does not mutate old bid rows.
- Admin auction cleanup removes related bid rows only as part of deletion cleanup.