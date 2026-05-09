/**
 * Custom exception hierarchy for the auction domain
 *
 * <p>All exceptions extend {@link com.auction.exception.AuctionException} to allow uniform handling
 * of domain-specific errors
 *
 * <p><b>Exception types:</b>
 *
 * <ul>
 *   <li>{@link com.auction.exception.NotFoundException} — entity lookup failures
 *   <li>{@link com.auction.exception.DuplicateException} — uniqueness violations
 *   <li>{@link com.auction.exception.InvalidBidException} — bid validation failures
 *   <li>{@link com.auction.exception.AuctionClosedException} — operations on closed auctions
 *   <li>{@link com.auction.exception.UnauthorizedException} — permission denials
 * </ul>
 *
 * <p><b>Usage pattern — catching all auction errors:</b>
 *
 * <pre>{@code
 * try {
 *     auctionService.createAuction(request, userId, role);
 * } catch (AuctionException e) {
 *     // Single catch handles all 5 specific exceptions
 *     return ApiResponse.error(e.getMessage());
 * }
 * }</pre>
 *
 * <p><b>Why RuntimeException:</b> All auction exceptions are unchecked because they typically
 * represent unrecoverable business rule violations or programming errors.Forcing {@code throws}
 * declarations everywhere would add noise without practical benefit
 */
package com.auction.exception;
