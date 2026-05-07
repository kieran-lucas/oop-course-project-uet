package com.auction.exception;

/**
 * Thrown when an operation would violate a uniqueness constraint, such as:
 *
 * <ul>
 *   <li>Registering a user with an existing email or username
 *   <li>Creating an item with a duplicate SKU
 *   <li>Inserting an entity whose primary key already exists
 * </ul>
 *
 * <p>Typical usage:
 *
 * <pre>{@code
 * if (userDao.existsByEmail(email)) {
 *     throw new DuplicateException("Email already registered: " + email);
 * }
 * }</pre>
 *
 * @see AuctionException
 */
public class DuplicateException extends AuctionException {

  private static final long serialVersionUID = 1L;

  /**
   * Constructs a new DuplicateException with the specified message.
   *
   * @param message description of the uniqueness violation
   */
  public DuplicateException(String message) {
    super(message);
  }

  /**
   * Constructs a new DuplicateException with the specified message and cause.
   *
   * @param message description of the uniqueness violation
   * @param cause the underlying exception (e.g., SQLIntegrityConstraintViolationException)
   */
  public DuplicateException(String message, Throwable cause) {
    super(message, cause);
  }
}
