package com.example.statemachine.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

class SqlQueryFunctionTest {
    // Approved query fingerprints, ignoring formatting whitespace.
    static Stream<Arguments> originalQueryFingerprints() {
        return Stream.of(
                Arguments.of(
                        SqlQueryConstants.WORKFLOW_DEFINITION_PARENT,
                        SqlQueryConstants.FIND_LATEST_ACTIVE_DEFINITION_ID,
                        "2a92bf3c787585241929c31eb7cd9381a60dcce67c36523dc8bd7c1639637536"),
                Arguments.of(
                        SqlQueryConstants.WORKFLOW_DEFINITION_PARENT,
                        SqlQueryConstants.FIND_DEFINITION_METADATA,
                        "1becffd77b2f5e1f4cad7398b5b7a135ef66d9e8b35bf427998678e35713f8c6"),
                Arguments.of(
                        SqlQueryConstants.WORKFLOW_DEFINITION_PARENT,
                        SqlQueryConstants.FIND_STATE_ACTIONS,
                        "aac932674a412aef3efb5a7f35f9517977b0227e25ba6e9ee4684680632bdd70"),
                Arguments.of(
                        SqlQueryConstants.WORKFLOW_DEFINITION_PARENT,
                        SqlQueryConstants.FIND_STATES,
                        "cf6189f9eb1f7aecd9e79f4d113ca11b356d9fbd828d23550192692771957291"),
                Arguments.of(
                        SqlQueryConstants.WORKFLOW_DEFINITION_PARENT,
                        SqlQueryConstants.FIND_TRANSITION_ACTIONS,
                        "0d5263001d67e625ab818c0c0a95095287e7cf731f0f643521189cf438a215f7"),
                Arguments.of(
                        SqlQueryConstants.WORKFLOW_DEFINITION_PARENT,
                        SqlQueryConstants.FIND_TRANSITIONS,
                        "68d85b87701180fab68f4ad2942b1a31e4f1e0284d751f62029af7a30126b901"),
                Arguments.of(
                        SqlQueryConstants.TRANSITION_HISTORY_PARENT,
                        SqlQueryConstants.INSERT_TRANSITION_HISTORY,
                        "a61209c75c5cfeb6c0419d7554fa1bfb431e31c819970b0280dc59b6da6beebc"),
                Arguments.of(
                        SqlQueryConstants.WORKFLOW_INSTANCE_PARENT,
                        SqlQueryConstants.INSERT_WORKFLOW_INSTANCE,
                        "95b900a8422f544812c2ea7961ac4739f28b44f50a990f57b0d1180fd471b05d"),
                Arguments.of(
                        SqlQueryConstants.WORKFLOW_INSTANCE_PARENT,
                        SqlQueryConstants.FIND_AND_LOCK_WORKFLOW_INSTANCE,
                        "82266d7f0777c7b1ac300a31877dff9f7135e51df3afd07dcfcc50f8ad588ccc"),
                Arguments.of(
                        SqlQueryConstants.WORKFLOW_INSTANCE_PARENT,
                        SqlQueryConstants.FIND_WORKFLOW_INSTANCE,
                        "85e1e2040c251e4a4e21cf4646e7f7f7271c35ae224156137aa7f6f32aada67e"),
                Arguments.of(
                        SqlQueryConstants.WORKFLOW_INSTANCE_PARENT,
                        SqlQueryConstants.UPDATE_CURRENT_STATE,
                        "802e697bfef7e3bb053c972f00f433649673a6e2b0222d19011cf48396a461b7"));
    }

    @ParameterizedTest
    @MethodSource("originalQueryFingerprints")
    void resourceQueryMatchesApprovedSql(
            String parent, String querySuffix, String expectedFingerprint) throws Exception {
        String query =
                SqlQueryFunction.GET_QUERY
                        .apply(parent, querySuffix)
                        .replaceAll("\\s+", " ")
                        .strip();
        byte[] fingerprint =
                MessageDigest.getInstance(WorkflowConstants.FINGERPRINT_ALGORITHM)
                        .digest(query.getBytes(StandardCharsets.UTF_8));
        assertEquals(
                expectedFingerprint, HexFormat.of().formatHex(fingerprint), parent + querySuffix);
    }

    @Test
    void rejectsUnknownQueryKeys() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SqlQueryFunction.GET_QUERY.apply(
                                SqlQueryConstants.WORKFLOW_DEFINITION_PARENT, ".missing-query"));
    }

    @Test
    void rejectsWrongParentEvenWhenSuffixExists() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SqlQueryFunction.GET_QUERY.apply(
                                SqlQueryConstants.WORKFLOW_INSTANCE_PARENT,
                                SqlQueryConstants.FIND_LATEST_ACTIVE_DEFINITION_ID));
    }

    @Test
    void rejectsMissingParentOrSuffix() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SqlQueryFunction.GET_QUERY.apply(
                                null, SqlQueryConstants.FIND_LATEST_ACTIVE_DEFINITION_ID));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SqlQueryFunction.GET_QUERY.apply(
                                SqlQueryConstants.WORKFLOW_DEFINITION_PARENT, " "));
    }
}
