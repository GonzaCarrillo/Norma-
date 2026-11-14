package com.norma.taller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:sqlite::memory:"})
@AutoConfigureMockMvc
class DeploymentSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Catalog catalog;

    @Test
    @DisplayName("GET /api/ping responde HTTP 200 con 'PONG' (contrato de healthcheck de despliegue)")
    void pingHealthcheckContract() throws Exception {
        mockMvc.perform(get("/api/ping"))
                .andExpect(status().isOk())
                .andExpect(content().string("PONG"));
    }

    @Test
    @DisplayName("Despliegue arranca con catálogo inicial accesible sin requerir credenciales externas")
    void deploymentStartsWithoutExternalCredentials() throws Exception {
        String session = "deploy_smoke_session";
        catalog.ensure(session);
        assertThat(catalog.all(session)).hasSize(8);
    }
}
