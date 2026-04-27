package com.auction.pattern.factory;

import com.auction.model.Art;
import com.auction.model.Electronics;
import com.auction.model.Item;
import com.auction.model.Vehicle;
import java.time.LocalDateTime;

public class ItemFactory {

    public static Item create(
        String name, String description, Long sellerId,
        String category, String categoryDetail) {

        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Category must not be empty");
        }

        LocalDateTime now = LocalDateTime.now();

        return switch (category.toUpperCase()) {
            case "ELECTRONICS" -> new Electronics(
                null, name, description, sellerId,
                categoryDetail, // brand
                now);

            case "ART" -> new Art(
                null, name, description, sellerId,
                categoryDetail, // artist
                now);

            case "VEHICLE" -> {
                // categoryDetail chứa năm sản xuất (String → int)
                int year;
                try {
                    year = Integer.parseInt(categoryDetail);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                        "Vehicle year must be a valid number, got: " + categoryDetail);
                }
                yield new Vehicle(
                    null, name, description, sellerId,
                    year,
                    now);
            }

            default -> throw new IllegalArgumentException(
                "Invalid category: '" + category + "'. "
                    + "Must be one of: ELECTRONICS, ART, VEHICLE");
        };
    }
}
