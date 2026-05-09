# Exception Package

Custom exception hierarchy for the auction domain.

## Hierarchy

```
RuntimeException
└── AuctionException (abstract)
    ├── NotFoundException
    ├── DuplicateException
    ├── InvalidBidException
    ├── AuctionClosedException
    └── UnauthorizedException
```

## Exception Reference

| Exception | When to throw | Example |
|---|---|---|
| `NotFoundException` | Entity lookup fails | `userDao.findById(id).orElseThrow(...)` |
| `DuplicateException` | Uniqueness violation | Registering existing email |
| `InvalidBidException` | Bid validation fails | Bid amount ≤ current price |
| `AuctionClosedException` | Operation on non-RUNNING auction | Bid on FINISHED auction |
| `UnauthorizedException` | Permission denied | BIDDER accessing SELLER endpoint |

## Usage

### Catching all auction errors

```java
try {
    auctionService.placeBid(bid);
} catch (AuctionException e) {
    log.error("Auction operation failed: {}", e.getMessage(), e);
    return ApiResponse.error(e.getMessage());
}
```

### Exception chaining

```java
try {
    return auctionDao.findById(id);
} catch (SQLException e) {
    throw new NotFoundException("Auction not found: " + id, e);
}
```

## Design Notes

- **Why RuntimeException:** Auction errors typically indicate violated business rules
  that callers cannot reasonably recover from. Forcing `throws` declarations
  everywhere would add noise without practical benefit.

- **Why abstract base class:** Allows callers to catch all domain-specific exceptions
  with a single `catch (AuctionException e)` block.

- **Why serialVersionUID:** Required because `RuntimeException` implements `Serializable`.
  Suppresses compiler warnings and ensures consistent serialization behavior.
