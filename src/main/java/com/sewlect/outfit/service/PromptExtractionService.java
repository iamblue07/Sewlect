package com.sewlect.outfit.service;

import com.sewlect.common.ai.config.DeepInfraConfig;
import com.sewlect.common.exception.ExternalServiceException;
import com.sewlect.common.logging.enums.ExternalCallOutcome;
import com.sewlect.common.logging.support.ExternalCallLogger;
import com.sewlect.common.taxonomy.enums.GarmentRole;
import com.sewlect.outfit.properties.OutfitPromptProperties;
import com.sewlect.outfit.domain.OutfitBlueprint;
import com.sewlect.outfit.domain.PromptInferredQuery;
import com.sewlect.outfit.domain.SlotDescription;
import com.sewlect.outfit.domain.StructuredPromptQuery;
import com.sewlect.outfit.enums.OutfitShapeViolation;
import com.sewlect.outfit.support.OutfitGenderFilterResolver;
import com.sewlect.outfit.support.OutfitShapeValidator;
import lombok.AllArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@AllArgsConstructor
@EnableConfigurationProperties(OutfitPromptProperties.class)
public class PromptExtractionService {

    private static final String OPERATION_EXTRACT = "extract-blueprints";
    private static final String OPERATION_EXTRACT_INFERRED = "extract-blueprints-inferred-filters";
    private static final String OPERATION_EXTRACT_SINGLE_SLOT = "extract-single-slot";

    private final OpenAiChatModel chatModel;
    private final OutfitPromptProperties properties;
    private final ExternalCallLogger externalCallLogger;
    private final OutfitShapeValidator outfitShapeValidator;

    public StructuredPromptQuery extract(String rawPrompt) {
        long startedAt = System.nanoTime();
        StructuredPromptQuery query;
        try {
            query = ChatClient.create(chatModel).prompt()
                    .user(buildPrompt(rawPrompt))
                    .call()
                    .entity(StructuredPromptQuery.class);
        } catch (RuntimeException e) {
            externalCallLogger.logCall(DeepInfraConfig.PROVIDER, OPERATION_EXTRACT, ExternalCallLogger.elapsedMillisSince(startedAt),
                    ExternalCallOutcome.PERMANENT_FAILURE);
            throw new ExternalServiceException("DeepInfra prompt extraction failed: " + e.getMessage());
        }

        if (query == null || query.blueprints() == null || query.blueprints().isEmpty()) {
            externalCallLogger.logCall(DeepInfraConfig.PROVIDER, OPERATION_EXTRACT, ExternalCallLogger.elapsedMillisSince(startedAt),
                    ExternalCallOutcome.PERMANENT_FAILURE);
            throw new ExternalServiceException("DeepInfra prompt extraction returned no usable blueprints");
        }

        externalCallLogger.logCall(DeepInfraConfig.PROVIDER, OPERATION_EXTRACT, ExternalCallLogger.elapsedMillisSince(startedAt), ExternalCallOutcome.SUCCESS);

        for (OutfitBlueprint blueprint : query.blueprints()) {
            validateShape(blueprint);
        }

        return query;
    }

    public PromptInferredQuery extractWithInferredFilters(String rawPrompt) {
        long startedAt = System.nanoTime();
        PromptInferredQuery query;
        try {
            query = ChatClient.create(chatModel).prompt()
                    .user(buildInferredFiltersPrompt(rawPrompt))
                    .call()
                    .entity(PromptInferredQuery.class);
        } catch (RuntimeException e) {
            externalCallLogger.logCall(DeepInfraConfig.PROVIDER, OPERATION_EXTRACT_INFERRED, ExternalCallLogger.elapsedMillisSince(startedAt),
                    ExternalCallOutcome.PERMANENT_FAILURE);
            throw new ExternalServiceException("DeepInfra prompt extraction failed: " + e.getMessage());
        }

        if (query == null || query.blueprints() == null || query.blueprints().isEmpty()) {
            externalCallLogger.logCall(DeepInfraConfig.PROVIDER, OPERATION_EXTRACT_INFERRED, ExternalCallLogger.elapsedMillisSince(startedAt),
                    ExternalCallOutcome.PERMANENT_FAILURE);
            throw new ExternalServiceException("DeepInfra prompt extraction returned no usable blueprints");
        }

        externalCallLogger.logCall(DeepInfraConfig.PROVIDER, OPERATION_EXTRACT_INFERRED, ExternalCallLogger.elapsedMillisSince(startedAt),
                ExternalCallOutcome.SUCCESS);

        for (OutfitBlueprint blueprint : query.blueprints()) {
            validateShape(blueprint);
        }

        validateGenders(query.genders());

        return query;
    }

    public String extractSingleSlot(String rawPrompt, GarmentRole role) {
        long startedAt = System.nanoTime();
        SlotDescription result;
        try {
            result = ChatClient.create(chatModel).prompt()
                    .user(buildSingleSlotPrompt(rawPrompt, role))
                    .call()
                    .entity(SlotDescription.class);
        } catch (RuntimeException e) {
            externalCallLogger.logCall(DeepInfraConfig.PROVIDER, OPERATION_EXTRACT_SINGLE_SLOT, ExternalCallLogger.elapsedMillisSince(startedAt),
                    ExternalCallOutcome.PERMANENT_FAILURE);
            throw new ExternalServiceException("DeepInfra single-slot prompt extraction failed: " + e.getMessage());
        }

        if (result == null || result.description() == null || result.description().isBlank()) {
            externalCallLogger.logCall(DeepInfraConfig.PROVIDER, OPERATION_EXTRACT_SINGLE_SLOT, ExternalCallLogger.elapsedMillisSince(startedAt),
                    ExternalCallOutcome.PERMANENT_FAILURE);
            throw new ExternalServiceException("DeepInfra single-slot prompt extraction returned no usable description");
        }

        externalCallLogger.logCall(DeepInfraConfig.PROVIDER, OPERATION_EXTRACT_SINGLE_SLOT, ExternalCallLogger.elapsedMillisSince(startedAt),
                ExternalCallOutcome.SUCCESS);

        return result.description();
    }

    private void validateShape(OutfitBlueprint blueprint) {
        List<GarmentRole> roles = blueprint.slots().stream().map(SlotDescription::role).toList();
        outfitShapeValidator.findViolation(roles).ifPresent(violation -> {
            throw new ExternalServiceException(shapeViolationMessage(violation, roles));
        });
    }

    private String shapeViolationMessage(OutfitShapeViolation violation, List<GarmentRole> roles) {
        return switch (violation) {
            case DUPLICATE_ROLE -> "Structured prompt extraction returned a blueprint with duplicate slot roles: " + roles;
            case INVALID_CORE ->
                    "Structured prompt extraction returned a blueprint violating the top+bottom/full_body shape rule: " + roles;
            case MISSING_FOOTWEAR -> "Structured prompt extraction returned a blueprint missing footwear: " + roles;
        };
    }

    private void validateGenders(Set<String> genders) {
        if (genders == null || genders.isEmpty()) {
            throw new ExternalServiceException("DeepInfra prompt extraction returned no inferred genders");
        }
        if (!OutfitGenderFilterResolver.ALL_GENDERS.containsAll(genders)) {
            throw new ExternalServiceException(
                    "DeepInfra prompt extraction returned an invalid gender category: " + genders);
        }
    }

    private String buildPrompt(String rawPrompt) {
        return """
                A user is looking for outfit ideas based on the following free-text request:
                "%s"

                Propose up to %d alternative outfit blueprints for this request (fewer is fine if only one shape makes sense).
                Each blueprint is a list of garment slots. Each slot has:
                - role: exactly one of TOP, BOTTOM, FULL_BODY, FOOTWEAR, OUTERWEAR, ACCESSORY (use these exact names)
                - description: a short natural-language description of what that slot should look like, based on the request

                Shape rules for every blueprint:
                - Either TOP and BOTTOM together, or FULL_BODY alone — never combine FULL_BODY with TOP or BOTTOM, and never include only one of TOP/BOTTOM without the other
                - FOOTWEAR is always required
                - OUTERWEAR and ACCESSORY are optional
                - Each role may appear at most once per blueprint
                """.formatted(rawPrompt, properties.maxBlueprints());
    }

    private String buildInferredFiltersPrompt(String rawPrompt) {
        return """
                A user is looking for outfit ideas based on the following free-text request, with no profile
                constraints applied - infer who the outfit is for and their budget directly from the request itself:
                "%s"

                Propose up to %d alternative outfit blueprints for this request (fewer is fine if only one shape makes sense).
                Each blueprint is a list of garment slots. Each slot has:
                - role: exactly one of TOP, BOTTOM, FULL_BODY, FOOTWEAR, OUTERWEAR, ACCESSORY (use these exact names)
                - description: a short natural-language description of what that slot should look like, based on the request

                Shape rules for every blueprint:
                - Either TOP and BOTTOM together, or FULL_BODY alone — never combine FULL_BODY with TOP or BOTTOM, and never include only one of TOP/BOTTOM without the other
                - FOOTWEAR is always required
                - OUTERWEAR and ACCESSORY are optional
                - Each role may appear at most once per blueprint

                Also infer, from the request alone:
                - genders: which of these categories the outfit is intended for - Men, Women, Boys, Girls, Unisex (use these exact names, pick one or more; if nothing in the request suggests a specific audience, use all five)
                - budget: a single number for the user's implied spending limit for the whole outfit, in EUR, if the request mentions or implies one (e.g. "under $100", "cheap", "budget-friendly" all imply a number); omit this field entirely if no budget is implied
                """.formatted(rawPrompt, properties.maxBlueprints());
    }

    private String buildSingleSlotPrompt(String rawPrompt, GarmentRole role) {
        return """
                A user wants to refine a single garment slot in their outfit based on the following request:
                "%s"

                The slot's role is fixed as %s and will not change - the request may reasonably ask for a different
                specific type of garment within that same role (for example, a different style of top), but never a
                different role entirely.

                Respond with a slot description object:
                - role: always "%s" (do not change this)
                - description: a short natural-language description of what this slot should now look like, based on the request
                """.formatted(rawPrompt, role, role);
    }
}