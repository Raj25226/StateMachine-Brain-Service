package com.example.statemachine.enums;

import com.example.statemachine.constants.SqlQueryConstants;
import com.example.statemachine.constants.WorkflowConstants;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlQueryFunctionTest {
    // Approved query fingerprints, ignoring formatting whitespace.
    static Stream<Arguments> originalQueryFingerprints() {
        return Stream.of(
                Arguments.of(SqlQueryConstants.WORKFLOW_DEFINITION_PARENT, SqlQueryConstants.FIND_LATEST_ACTIVE_DEFINITION_ID, "2a92bf3c787585241929c31eb7cd9381a60dcce67c36523dc8bd7c1639637536"),
                Arguments.of(SqlQueryConstants.WORKFLOW_DEFINITION_PARENT, SqlQueryConstants.FIND_DEFINITION_METADATA, "1becffd77b2f5e1f4cad7398b5b7a135ef66d9e8b35bf427998678e35713f8c6"),
                Arguments.of(SqlQueryConstants.WORKFLOW_DEFINITION_PARENT, SqlQueryConstants.FIND_STATE_ACTIONS, "aac932674a412aef3efb5a7f35f9517977b0227e25ba6e9ee4684680632bdd70"),
                Arguments.of(SqlQueryConstants.WORKFLOW_DEFINITION_PARENT, SqlQueryConstants.FIND_STATES, "cf6189f9eb1f7aecd9e79f4d113ca11b356d9fbd828d23550192692771957291"),
                Arguments.of(SqlQueryConstants.WORKFLOW_DEFINITION_PARENT, SqlQueryConstants.FIND_TRANSITION_ACTIONS, "0d5263001d67e625ab818c0c0a95095287e7cf731f0f643521189cf438a215f7"),
                Arguments.of(SqlQueryConstants.WORKFLOW_DEFINITION_PARENT, SqlQueryConstants.FIND_TRANSITIONS, "cf9be2f21f857d16a07f696b9dd2e80f370d83d029223187d2439857424e4100"),
                Arguments.of(SqlQueryConstants.WORKFLOW_DEFINITION_PARENT, SqlQueryConstants.FIND_DEFERRED_EVENTS, "99f92e394f100eb11a3eb4a23b4cbc02cc98943f382f3cdfe1fb0cebe0727046"),
                Arguments.of(SqlQueryConstants.TRANSITION_HISTORY_PARENT, SqlQueryConstants.INSERT_TRANSITION_HISTORY, "a61209c75c5cfeb6c0419d7554fa1bfb431e31c819970b0280dc59b6da6beebc"),
                Arguments.of(SqlQueryConstants.IDEMPOTENCY_PARENT, SqlQueryConstants.EXISTS_BY_IDEMPOTENCY_KEY, "e6e0fb16e95dac538e4919e1f6bccb9c6ad59299f95a0ec09c0323b940150002"),
                Arguments.of(SqlQueryConstants.IDEMPOTENCY_PARENT, SqlQueryConstants.RESERVE_IDEMPOTENCY_KEY, "ffe4ed8d819c8ae70a9642c89e2165cf6cac8a6c5d915e07a6477a4721c48550"),
                Arguments.of(SqlQueryConstants.IDEMPOTENCY_PARENT, SqlQueryConstants.FIND_AND_LOCK_IDEMPOTENCY_RECORD, "c629c5c186615e43f0e4abb328ffed47e2d44f2bb8f919b8a120b9b8695dca01"),
                Arguments.of(SqlQueryConstants.IDEMPOTENCY_PARENT, SqlQueryConstants.SAVE_SUCCESSFUL_RESPONSE, "862dc84f3dc417ae277a11c950ca2fa02273203a4fdfcbdb87a3db3a67b1cb86"),
                Arguments.of(SqlQueryConstants.WORKFLOW_INSTANCE_PARENT, SqlQueryConstants.INSERT_WORKFLOW_INSTANCE, "95b900a8422f544812c2ea7961ac4739f28b44f50a990f57b0d1180fd471b05d"),
                Arguments.of(SqlQueryConstants.WORKFLOW_INSTANCE_PARENT, SqlQueryConstants.FIND_AND_LOCK_WORKFLOW_INSTANCE, "d6cac33cea63f41d0edfb3454dac11c0ce8750189f337f3cb40d63c24a701e4d"),
                Arguments.of(SqlQueryConstants.WORKFLOW_INSTANCE_PARENT, SqlQueryConstants.FIND_WORKFLOW_INSTANCE, "3f03a43161eb83172631905dd04dbcc9fcf89a4902ddd1f22bace7683c1f9121"),
                Arguments.of(SqlQueryConstants.WORKFLOW_INSTANCE_PARENT, SqlQueryConstants.UPDATE_WORKFLOW_CONTEXT, "71827d0130e4dde1e0e2702d13e721ec07c17e5969e49d8a73aa791dea9643ac"));
    }

    @ParameterizedTest
    @MethodSource("originalQueryFingerprints")
    void resourceQueryMatchesApprovedSql(String parent, String querySuffix, String expectedFingerprint) throws Exception {
        String query = SqlQueryFunction.GET_QUERY.apply(parent, querySuffix).replaceAll("\\s+", " ").strip();
        byte[] fingerprint = MessageDigest.getInstance(WorkflowConstants.FINGERPRINT_ALGORITHM)
                .digest(query.getBytes(StandardCharsets.UTF_8));
        assertEquals(expectedFingerprint, HexFormat.of().formatHex(fingerprint), parent + querySuffix);
    }

    @Test
    void rejectsUnknownQueryKeys() {
        assertThrows(IllegalArgumentException.class, () -> SqlQueryFunction.GET_QUERY.apply(SqlQueryConstants.WORKFLOW_DEFINITION_PARENT, ".missing-query"));
    }

    @Test
    void rejectsWrongParentEvenWhenSuffixExists() {
        assertThrows(IllegalArgumentException.class, () -> SqlQueryFunction.GET_QUERY.apply(
                SqlQueryConstants.IDEMPOTENCY_PARENT, SqlQueryConstants.FIND_LATEST_ACTIVE_DEFINITION_ID));
    }

    @Test
    void rejectsMissingParentOrSuffix() {
        assertThrows(IllegalArgumentException.class, () -> SqlQueryFunction.GET_QUERY.apply(
                null, SqlQueryConstants.FIND_LATEST_ACTIVE_DEFINITION_ID));
        assertThrows(IllegalArgumentException.class, () -> SqlQueryFunction.GET_QUERY.apply(
                SqlQueryConstants.WORKFLOW_DEFINITION_PARENT, " "));
    }
}
