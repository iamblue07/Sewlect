package com.sewlect.common.taxonomy.support;

import com.sewlect.common.taxonomy.enums.GarmentRole;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class GarmentRoleResolver {

    private static final Map<String, GarmentRole> ARTICLE_TYPE_TO_ROLE = Map.ofEntries(
            // TOP
            Map.entry("Kurtas", GarmentRole.TOP),
            Map.entry("Kurtis", GarmentRole.TOP),
            Map.entry("Shirts", GarmentRole.TOP),
            Map.entry("Sweaters", GarmentRole.TOP),
            Map.entry("Sweatshirts", GarmentRole.TOP),
            Map.entry("Tops", GarmentRole.TOP),
            Map.entry("Tshirts", GarmentRole.TOP),
            Map.entry("Tunics", GarmentRole.TOP),

            // BOTTOM
            Map.entry("Capris", GarmentRole.BOTTOM),
            Map.entry("Churidar", GarmentRole.BOTTOM),
            Map.entry("Jeans", GarmentRole.BOTTOM),
            Map.entry("Jeggings", GarmentRole.BOTTOM),
            Map.entry("Leggings", GarmentRole.BOTTOM),
            Map.entry("Patiala", GarmentRole.BOTTOM),
            Map.entry("Rain Trousers", GarmentRole.BOTTOM),
            Map.entry("Salwar", GarmentRole.BOTTOM),
            Map.entry("Shorts", GarmentRole.BOTTOM),
            Map.entry("Skirts", GarmentRole.BOTTOM),
            Map.entry("Tights", GarmentRole.BOTTOM),
            Map.entry("Track Pants", GarmentRole.BOTTOM),
            Map.entry("Trousers", GarmentRole.BOTTOM),

            // FULL_BODY
            Map.entry("Dresses", GarmentRole.FULL_BODY),
            Map.entry("Jumpsuit", GarmentRole.FULL_BODY),
            Map.entry("Lehenga Choli", GarmentRole.FULL_BODY),
            Map.entry("Rompers", GarmentRole.FULL_BODY),
            Map.entry("Sarees", GarmentRole.FULL_BODY),

            // FOOTWEAR
            Map.entry("Casual Shoes", GarmentRole.FOOTWEAR),
            Map.entry("Flats", GarmentRole.FOOTWEAR),
            Map.entry("Formal Shoes", GarmentRole.FOOTWEAR),
            Map.entry("Heels", GarmentRole.FOOTWEAR),
            Map.entry("Sandals", GarmentRole.FOOTWEAR),
            Map.entry("Sports Sandals", GarmentRole.FOOTWEAR),
            Map.entry("Sports Shoes", GarmentRole.FOOTWEAR),
            Map.entry("Flip Flops", GarmentRole.FOOTWEAR),
            Map.entry("Booties", GarmentRole.FOOTWEAR),

            // OUTERWEAR
            Map.entry("Blazers", GarmentRole.OUTERWEAR),
            Map.entry("Jackets", GarmentRole.OUTERWEAR),
            Map.entry("Nehru Jackets", GarmentRole.OUTERWEAR),
            Map.entry("Rain Jacket", GarmentRole.OUTERWEAR),
            Map.entry("Shrug", GarmentRole.OUTERWEAR),
            Map.entry("Suits", GarmentRole.OUTERWEAR),
            Map.entry("Waistcoat", GarmentRole.OUTERWEAR),

            // ACCESSORY
            Map.entry("Backpacks", GarmentRole.ACCESSORY),
            Map.entry("Bangle", GarmentRole.ACCESSORY),
            Map.entry("Belts", GarmentRole.ACCESSORY),
            Map.entry("Bracelet", GarmentRole.ACCESSORY),
            Map.entry("Caps", GarmentRole.ACCESSORY),
            Map.entry("Clutches", GarmentRole.ACCESSORY),
            Map.entry("Cufflinks", GarmentRole.ACCESSORY),
            Map.entry("Duffel Bag", GarmentRole.ACCESSORY),
            Map.entry("Dupatta", GarmentRole.ACCESSORY),
            Map.entry("Earrings", GarmentRole.ACCESSORY),
            Map.entry("Gloves", GarmentRole.ACCESSORY),
            Map.entry("Hair Accessory", GarmentRole.ACCESSORY),
            Map.entry("Handbags", GarmentRole.ACCESSORY),
            Map.entry("Hat", GarmentRole.ACCESSORY),
            Map.entry("Headband", GarmentRole.ACCESSORY),
            Map.entry("Jewellery Set", GarmentRole.ACCESSORY),
            Map.entry("Laptop Bag", GarmentRole.ACCESSORY),
            Map.entry("Messenger Bag", GarmentRole.ACCESSORY),
            Map.entry("Mobile Pouch", GarmentRole.ACCESSORY),
            Map.entry("Mufflers", GarmentRole.ACCESSORY),
            Map.entry("Necklace and Chains", GarmentRole.ACCESSORY),
            Map.entry("Pendant", GarmentRole.ACCESSORY),
            Map.entry("Ring", GarmentRole.ACCESSORY),
            Map.entry("Rucksacks", GarmentRole.ACCESSORY),
            Map.entry("Scarves", GarmentRole.ACCESSORY),
            Map.entry("Stoles", GarmentRole.ACCESSORY),
            Map.entry("Sunglasses", GarmentRole.ACCESSORY),
            Map.entry("Suspenders", GarmentRole.ACCESSORY),
            Map.entry("Tablet Sleeve", GarmentRole.ACCESSORY),
            Map.entry("Ties", GarmentRole.ACCESSORY),
            Map.entry("Ties and Cufflinks", GarmentRole.ACCESSORY),
            Map.entry("Travel Accessory", GarmentRole.ACCESSORY),
            Map.entry("Trolley Bag", GarmentRole.ACCESSORY),
            Map.entry("Waist Pouch", GarmentRole.ACCESSORY),
            Map.entry("Wallets", GarmentRole.ACCESSORY),
            Map.entry("Wristbands", GarmentRole.ACCESSORY)
    );

    private static final Map<GarmentRole, List<String>> ARTICLE_TYPES_BY_ROLE = groupArticleTypesByRole();

    public Map<GarmentRole, List<String>> articleTypesByRole() {
        return ARTICLE_TYPES_BY_ROLE;
    }

    private static Map<GarmentRole, List<String>> groupArticleTypesByRole() {
        Map<GarmentRole, List<String>> grouped = new EnumMap<>(GarmentRole.class);
        ARTICLE_TYPE_TO_ROLE.forEach((articleType, role) ->
                grouped.computeIfAbsent(role, ignored -> new ArrayList<>()).add(articleType));
        grouped.replaceAll((role, articleTypes) -> articleTypes.stream().sorted().toList());
        return Collections.unmodifiableMap(grouped);
    }

    public Optional<GarmentRole> resolve(String articleType) {
        if (articleType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(ARTICLE_TYPE_TO_ROLE.get(articleType));
    }
}