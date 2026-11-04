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
    // Fingerprints captured from the original inline SQL, ignoring formatting whitespace.
    static Stream<Arguments> originalQueryFingerprints() {
        return Stream.of(
                Arguments.of(SqlQueryConstants.WORKFLOW_DEFINITION_PARENT, SqlQueryConstants.FIND_LATEST_ACTIVE_DEFINITION_ID, "d5fb872fc923c2c49daea20e174e30492d43298a5d9eeab3b1c89331dd833a86"),
                Arguments.of(SqlQueryConstants.WORKFLOW_DEFINITION_PARENT, SqlQueryConstants.FIND_DEFINITION_METADATA, "65f56b81f23344d4a9ad1ae71d7a7412b78826438bb5f9a99335ff548381ea51"),
                Arguments.of(SqlQueryConstants.WORKFLOW_DEFINITION_PARENT, SqlQueryConstants.FIND_STATE_ACTIONS, "b622971cbef136128d197698c8883911a8a0220d7085a7ed01cd0f107b5d93f4"),
                Arguments.of(SqlQueryConstants.WORKFLOW_DEFINITION_PARENT, SqlQueryConstants.FIND_STATES, "26ec9ae6c8fb23699ebe0b6b56107f5a89bce1cc5b663af8572fefb426fafcd8"),
                Arguments.of(SqlQueryConstants.WORKFLOW_DEFINITION_PARENT, SqlQueryConstants.FIND_TRANSITION_ACTIONS, "eae49fcdb0d98c2ac97c468875447aba9b3253e53c66e61d86ac74fb847892d4"),
                Arguments.of(SqlQueryConstants.WORKFLOW_DEFINITION_PARENT, SqlQueryConstants.FIND_TRANSITIONS, "305e7aaedd94ad39072ba2d0af0db09c1324b1ba17cd9d50ba5c78818fcfca8b"),
                Arguments.of(SqlQueryConstants.WORKFLOW_DEFINITION_PARENT, SqlQueryConstants.FIND_DEFERRED_EVENTS, "6fc143d2a15525c23704c49fa1d8fafaeab6b641d98378a03d7d89342c15e89a"),
                Arguments.of(SqlQueryConstants.TRANSITION_HISTORY_PARENT, SqlQueryConstants.INSERT_TRANSITION_HISTORY, "11a9682e3f84ccd2cf2cfd952b09f60faabde0b919a007f7aff26687f7339546"),
                Arguments.of(SqlQueryConstants.IDEMPOTENCY_PARENT, SqlQueryConstants.EXISTS_BY_IDEMPOTENCY_KEY, "e6e0fb16e95dac538e4919e1f6bccb9c6ad59299f95a0ec09c0323b940150002"),
                Arguments.of(SqlQueryConstants.IDEMPOTENCY_PARENT, SqlQueryConstants.RESERVE_IDEMPOTENCY_KEY, "ffe4ed8d819c8ae70a9642c89e2165cf6cac8a6c5d915e07a6477a4721c48550"),
                Arguments.of(SqlQueryConstants.IDEMPOTENCY_PARENT, SqlQueryConstants.FIND_AND_LOCK_IDEMPOTENCY_RECORD, "1c441321d52c5aba0516cc9c7f03ccd9aa4388f5e75e30520b896857d64ba5b9"),
                Arguments.of(SqlQueryConstants.IDEMPOTENCY_PARENT, SqlQueryConstants.SAVE_SUCCESSFUL_RESPONSE, "862dc84f3dc417ae277a11c950ca2fa02273203a4fdfcbdb87a3db3a67b1cb86"),
                Arguments.of(SqlQueryConstants.WORKFLOW_INSTANCE_PARENT, SqlQueryConstants.INSERT_WORKFLOW_INSTANCE, "132adeb976f02c580c6b6c9208503e0e4439ac20e09e43fbe8f72423c21ada2b"),
                Arguments.of(SqlQueryConstants.WORKFLOW_INSTANCE_PARENT, SqlQueryConstants.FIND_AND_LOCK_WORKFLOW_INSTANCE, "e90b46b409c0b0493000e57cd060690b8dac2491ab4ab516388df3b3c4ce3d99"),
                Arguments.of(SqlQueryConstants.WORKFLOW_INSTANCE_PARENT, SqlQueryConstants.FIND_WORKFLOW_INSTANCE, "84321ad848beff3f0ab222cbdd42e04d5831149a5957ae1c6ca65434fde24168"),
                Arguments.of(SqlQueryConstants.WORKFLOW_INSTANCE_PARENT, SqlQueryConstants.UPDATE_WORKFLOW_CONTEXT, "25640c92ea094f744d243b0920e24f6a5e764ce745ddd13652d1ffb8a8ce6a4e"));
    }

    @ParameterizedTest
    @MethodSource("originalQueryFingerprints")
    void resourceQueryPreservesOriginalSql(String parent, String querySuffix, String expectedFingerprint) throws Exception {
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
