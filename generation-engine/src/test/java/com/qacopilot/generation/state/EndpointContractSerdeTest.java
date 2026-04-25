package com.qacopilot.generation.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qacopilot.analysis.ir.AuthContract;
import com.qacopilot.analysis.ir.EndpointContract;
import com.qacopilot.analysis.ir.FieldContract;
import com.qacopilot.analysis.ir.ParamBinding;
import com.qacopilot.analysis.ir.RequestBodyContract;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndpointContractSerdeTest {

    @Test
    void roundTripsEndpointContract() throws Exception {
        EndpointContract original =
                new EndpointContract(
                        "POST",
                        "/api/x",
                        "XCtrl",
                        "demo.XCtrl",
                        "postIt",
                        "api",
                        "x",
                        List.of(new ParamBinding("id", "String", true, "")),
                        List.of(),
                        RequestBodyContract.of(
                                "demo.Dto",
                                true,
                                List.of(new FieldContract("a", "int", true, List.of("Min(1)")))
                        ),
                        List.of("application/json"),
                        List.of("application/json"),
                        new AuthContract("methodSecurity", true),
                        List.of("warn1"),
                        "summary",
                        List.of("t1"),
                        List.of(200, 422),
                        "createX",
                        "Spec description",
                        List.of("api.yaml"),
                        "src/M.java"
                );

        ObjectNode node = EndpointContractSerde.toObjectNode(original);
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(node);
        ObjectNode readBack = (ObjectNode) mapper.readTree(json);
        EndpointContract restored = EndpointContractSerde.fromObjectNode(readBack);

        assertEquals(original.getHttpMethod(), restored.getHttpMethod());
        assertEquals(original.getPath(), restored.getPath());
        assertEquals(original.getOpenApiSummary(), restored.getOpenApiSummary());
        assertEquals(original.getOpenApiResponseCodes(), restored.getOpenApiResponseCodes());
        assertEquals(original.getOpenApiSpecOperationId(), restored.getOpenApiSpecOperationId());
        assertEquals(original.getOpenApiSpecDescription(), restored.getOpenApiSpecDescription());
        assertEquals(original.getOpenApiSpecSources(), restored.getOpenApiSpecSources());
        assertEquals(1, restored.getRequestBody().getFields().size());
        assertTrue(restored.getRequestBody().getFields().get(0).getValidations().contains("Min(1)"));
    }
}
