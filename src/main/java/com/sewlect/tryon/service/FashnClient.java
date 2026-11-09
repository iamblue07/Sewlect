package com.sewlect.tryon.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sewlect.common.exception.ExternalServiceException;
import com.sewlect.common.logging.enums.ExternalCallOutcome;
import com.sewlect.common.logging.support.ExternalCallLogger;
import com.sewlect.tryon.domain.FashnPredictionResult;
import com.sewlect.tryon.properties.FashnModelProperties;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@AllArgsConstructor
@EnableConfigurationProperties(FashnModelProperties.class)
public class FashnClient {

    private static final String PROVIDER = "fashn";
    private static final String OPERATION_SUBMIT_V16 = "submit-tryon-v16";
    private static final String OPERATION_SUBMIT_MAX = "submit-tryon-max";
    private static final String OPERATION_POLL = "poll-prediction";

    private final RestClient fashnRestClient;
    private final FashnModelProperties modelProperties;
    private final ExternalCallLogger externalCallLogger;

    public String submitTryonV16(String modelImageUrl, String garmentImageUrl, String category) {
        FashnRunRequest request = new FashnRunRequest(
                modelProperties.v16ModelName(),
                new FashnRunInputs(modelImageUrl, garmentImageUrl, category,
                        modelProperties.v16Mode(), modelProperties.outputFormat()));
        return submit(request, OPERATION_SUBMIT_V16);
    }

    public String submitTryonMax(String modelImageUrl, String productImageUrl) {
        FashnMaxRunRequest request = new FashnMaxRunRequest(
                modelProperties.maxModelName(),
                new FashnMaxRunInputs(modelImageUrl, productImageUrl, modelProperties.maxResolution(),
                        modelProperties.maxGenerationMode(), modelProperties.outputFormat()));
        return submit(request, OPERATION_SUBMIT_MAX);
    }

    public FashnPredictionResult poll(String predictionId) {
        long startedAt = System.nanoTime();
        FashnPredictionResult result;
        try {
            result = fashnRestClient.get()
                    .uri("/status/{id}", predictionId)
                    .retrieve()
                    .body(FashnPredictionResult.class);
        } catch (RestClientException e) {
            externalCallLogger.logCall(PROVIDER, OPERATION_POLL, ExternalCallLogger.elapsedMillisSince(startedAt),
                    ExternalCallOutcome.RETRYABLE_FAILURE);
            throw new ExternalServiceException(
                    "FASHN poll call failed for prediction " + predictionId + ": " + e.getMessage());
        }

        if (result == null) {
            externalCallLogger.logCall(PROVIDER, OPERATION_POLL, ExternalCallLogger.elapsedMillisSince(startedAt),
                    ExternalCallOutcome.PERMANENT_FAILURE);
            throw new ExternalServiceException("FASHN /v1/status returned no body for prediction " + predictionId);
        }

        externalCallLogger.logCall(PROVIDER, OPERATION_POLL, ExternalCallLogger.elapsedMillisSince(startedAt), ExternalCallOutcome.SUCCESS);
        return result;
    }

    private String submit(Object request, String operation) {
        long startedAt = System.nanoTime();
        FashnPredictionResult result;
        try {
            result = fashnRestClient.post()
                    .uri("/run")
                    .body(request)
                    .retrieve()
                    .body(FashnPredictionResult.class);
        } catch (RestClientException e) {
            externalCallLogger.logCall(PROVIDER, operation, ExternalCallLogger.elapsedMillisSince(startedAt),
                    ExternalCallOutcome.RETRYABLE_FAILURE);
            throw new ExternalServiceException("FASHN submit call failed: " + e.getMessage());
        }

        if (result == null || result.id() == null) {
            externalCallLogger.logCall(PROVIDER, operation, ExternalCallLogger.elapsedMillisSince(startedAt),
                    ExternalCallOutcome.PERMANENT_FAILURE);
            throw new ExternalServiceException("FASHN /v1/run returned no prediction id");
        }

        externalCallLogger.logCall(PROVIDER, operation, ExternalCallLogger.elapsedMillisSince(startedAt), ExternalCallOutcome.SUCCESS);
        return result.id();
    }

    private record FashnRunRequest(
            @JsonProperty("model_name") String modelName,
            FashnRunInputs inputs) {
    }

    private record FashnRunInputs(
            @JsonProperty("model_image") String modelImage,
            @JsonProperty("garment_image") String garmentImage,
            String category,
            String mode,
            @JsonProperty("output_format") String outputFormat) {
    }

    private record FashnMaxRunRequest(
            @JsonProperty("model_name") String modelName,
            FashnMaxRunInputs inputs) {
    }

    private record FashnMaxRunInputs(
            @JsonProperty("model_image") String modelImage,
            @JsonProperty("product_image") String productImage,
            String resolution,
            @JsonProperty("generation_mode") String generationMode,
            @JsonProperty("output_format") String outputFormat) {
    }
}