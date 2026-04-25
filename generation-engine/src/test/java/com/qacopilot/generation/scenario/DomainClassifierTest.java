package com.qacopilot.generation.scenario;

import com.qacopilot.analysis.ir.EndpointContract;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DomainClassifierTest {

    @Test
    void derivesFromController() {
        var e =
                EndpointContract.synthetic(
                        "GET", "/api/v1/users", "UserManagementController", "list"
                );
        assertEquals("user_management", DomainClassifier.domainKey(e));
        assertEquals("User Management", DomainClassifier.domainTitle("user_management"));
    }

    @Test
    void fallsBackToPathWhenNoController() {
        var e = EndpointContract.synthetic("GET", "/api/products", "", "x");
        assertEquals("products", DomainClassifier.domainKey(e));
    }
}
