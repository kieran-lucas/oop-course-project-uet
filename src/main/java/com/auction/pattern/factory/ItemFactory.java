package com.auction.pattern.factory;

import com.auction.dto.CreateItemRequest;
import com.auction.model.Art;
import com.auction.model.Electronics;
import com.auction.model.Item;
import com.auction.model.Vehicle;

/** Factory that creates the concrete Item subtype requested by category. */
public final class ItemFactory {

  private ItemFactory() {
    throw new UnsupportedOperationException("ItemFactory is a utility class");
  }

  public static Item create(CreateItemRequest req, Long sellerId) {
    String category = req.getCategory().toUpperCase().trim();

    return switch (category) {
      case "ELECTRONICS" ->
          new Electronics(req.getName(), req.getDescription(), sellerId, req.getCategoryDetail());
      case "ART" -> new Art(req.getName(), req.getDescription(), sellerId, req.getCategoryDetail());
      case "VEHICLE" ->
          new Vehicle(
              req.getName(), req.getDescription(), sellerId, parseYear(req.getCategoryDetail()));
      default ->
          throw new IllegalArgumentException(
              "Invalid category: '"
                  + req.getCategory()
                  + "'. Accepted values: ELECTRONICS, ART, VEHICLE");
    };
  }

  private static int parseYear(String yearText) {
    int year = Integer.parseInt(yearText.trim());
    if (year < 1886 || year > 2100) {
      throw new IllegalArgumentException("Invalid manufacture year (1886-2100): " + year);
    }
    return year;
  }
}
