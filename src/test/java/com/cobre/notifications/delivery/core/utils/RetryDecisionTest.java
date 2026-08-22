package com.cobre.notifications.delivery.core.utils;

import com.cobre.notifications.delivery.core.domain.HttpResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class RetryDecisionTest {

    @ParameterizedTest
    @ValueSource(ints = {200, 201, 204, 299})
    void classifiesSuccessCodesAsSuccess(int statusCode) {
        assertThat(RetryDecision.classify(new HttpResult(statusCode, null)))
                .isEqualTo(RetryDecision.Classification.SUCCESS);
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500, 502, 503, 599})
    void classifiesTimeoutServerErrorsAndRateLimitAsTransient(int statusCode) {
        assertThat(RetryDecision.classify(new HttpResult(statusCode, null)))
                .isEqualTo(RetryDecision.Classification.TRANSIENT);
    }

    @Test
    void classifiesMissingStatusCodeAsTransient() {
        assertThat(RetryDecision.classify(new HttpResult(null, null)))
                .isEqualTo(RetryDecision.Classification.TRANSIENT);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 422})
    void classifiesRemaining4xxAsPermanent(int statusCode) {
        assertThat(RetryDecision.classify(new HttpResult(statusCode, null)))
                .isEqualTo(RetryDecision.Classification.PERMANENT);
    }
}
