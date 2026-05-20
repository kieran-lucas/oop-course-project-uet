package com.auction.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.LocalDateTime;

/**
 * Base class for an auction item.
 *
 * <p>Only fields shared by every item type live here. Category-specific details belong in
 * subclasses such as {@link Electronics}, {@link Art}, and {@link Vehicle}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "category", visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = Electronics.class, name = "ELECTRONICS"),
  @JsonSubTypes.Type(value = Art.class, name = "ART"),
  @JsonSubTypes.Type(value = Vehicle.class, name = "VEHICLE")
})
public abstract class Item extends Entity {

  private String name;
  private String description;
  private Long sellerId;
  private String status = "AVAILABLE"; // AVAILABLE, IN_AUCTION, SOLD, REMOVED

  /** Constructor for frameworks/Jackson. */
  protected Item() {
    super();
  }

  /** Creates a new item before persistence assigns an id. */
  protected Item(String name, String description, Long sellerId) {
    super();
    this.name = name;
    this.description = description;
    this.sellerId = sellerId;
  }

  /** Rehydrates an item from an existing database row. */
  protected Item(
      Long id,
      String name,
      String description,
      Long sellerId,
      String status,
      LocalDateTime createdAt) {
    super(id, createdAt);
    this.name = name;
    this.description = description;
    this.sellerId = sellerId;
    this.status = status == null ? "AVAILABLE" : status;
  }

  public abstract String getCategory();

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Long getSellerId() {
    return sellerId;
  }

  public void setSellerId(Long sellerId) {
    this.sellerId = sellerId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  @Override
  public String toString() {
    return getCategory()
        + "{name='"
        + name
        + "', status='"
        + status
        + "', seller="
        + sellerId
        + "}";
  }
}
