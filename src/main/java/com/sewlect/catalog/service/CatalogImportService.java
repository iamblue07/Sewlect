package com.sewlect.catalog.service;

import com.sewlect.catalog.entity.Product;
import com.sewlect.catalog.entity.ProductVariant;
import com.sewlect.catalog.pipeline.CatalogCsvReader;
import com.sewlect.catalog.pipeline.StyleCsvRecord;
import com.sewlect.catalog.repository.ProductRepository;
import com.sewlect.catalog.repository.ProductVariantRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

@Slf4j
@Service
@AllArgsConstructor
public class CatalogImportService {

    private static final int BATCH_SIZE = 500;
    private static final List<String> VARIANT_SIZES = List.of("S", "M", "L", "XL");
    private static final int MAX_STOCK_QUANTITY = 50;
    private  final Random random;

    private final CatalogCsvReader catalogCsvReader;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;

    public void importCatalog(Path stylesCsvPath, Path imagesCsvPath) {
        Map<String, String> imageLinksByFilename = catalogCsvReader.readImageLinks(imagesCsvPath);
        List<StyleCsvRecord> styleRecords = catalogCsvReader.readStyles(stylesCsvPath);

        Set<String> existingExternalIds = new HashSet<>(productRepository.findAllExternalIds());

        int created = 0;
        int skippedDuplicate = 0;
        int skippedNoImage = 0;
        List<Product> pendingProducts = new ArrayList<>(BATCH_SIZE);

        for (StyleCsvRecord record : styleRecords) {
            String imageUrl = imageLinksByFilename.get(record.id() + ".jpg");

            if (imageUrl == null) {
                log.warn("Skipping external id {}: no matching image", record.id());
                skippedNoImage++;
                continue;
            }
            if (existingExternalIds.contains(record.id())) {
                skippedDuplicate++;
                continue;
            }

            Product product = toProduct(record, imageUrl);
            existingExternalIds.add(record.id());
            pendingProducts.add(product);
            created++;

            if (pendingProducts.size() == BATCH_SIZE) {
                saveChunk(pendingProducts);
                pendingProducts.clear();
            }
        }

        if (!pendingProducts.isEmpty()) {
            saveChunk(pendingProducts);
        }

        logSummary(created, skippedDuplicate, skippedNoImage);
    }

    private Product toProduct(StyleCsvRecord record, String imageUrl) {
        return Product.builder()
                .externalId(record.id())
                .gender(record.gender())
                .masterCategory(record.masterCategory())
                .subCategory(record.subCategory())
                .articleType(record.articleType())
                .baseColour(record.baseColour())
                .season(record.season())
                .year(record.year())
                .usage(record.usage())
                .productDisplayName(record.productDisplayName())
                .imageUrl(imageUrl)
                .build();
    }

    private void saveChunk(List<Product> products) {
        List<Product> savedProducts = productRepository.saveAll(products);

        List<ProductVariant> variants = savedProducts.stream()
                .flatMap(product -> generateVariants(product).stream())
                .toList();

        productVariantRepository.saveAll(variants);
    }

    private List<ProductVariant> generateVariants(Product product) {
        return VARIANT_SIZES.stream()
                .map(size -> toVariant(product, size))
                .toList();
    }

    private ProductVariant toVariant(Product product, String size) {
        return ProductVariant.builder()
                .product(product)
                .size(size)
                .stockQuantity(random.nextInt(MAX_STOCK_QUANTITY + 1))
                .build();
    }

    private void logSummary(int created, int skippedDuplicate, int skippedNoImage) {
        log.info("Catalog import complete: {} created, {} skipped (duplicate), {} skipped (no image)",
                created, skippedDuplicate, skippedNoImage);
    }
}