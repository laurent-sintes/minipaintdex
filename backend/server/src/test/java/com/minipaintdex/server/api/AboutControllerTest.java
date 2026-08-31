package com.minipaintdex.server.api;

import com.minipaintdex.bootstrap.MiniPaintDexProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Properties;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AboutControllerTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var properties = mock(MiniPaintDexProperties.class);
        when(properties.application()).thenReturn(
                new MiniPaintDexProperties.Application("Mini Paint Dex", "Laurent Sintès"));

        var values = new Properties();
        values.setProperty("version", "test-version");
        @SuppressWarnings("unchecked")
        var buildProperties = (ObjectProvider<BuildProperties>) mock(ObjectProvider.class);
        when(buildProperties.getIfAvailable()).thenReturn(new BuildProperties(values));

        mvc = MockMvcBuilders.standaloneSetup(new AboutController(properties, buildProperties)).build();
    }

    @Test
    void exposesConfiguredIdentityAndBuildVersion() throws Exception {
        mvc.perform(get("/api/v1/about"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mini Paint Dex"))
                .andExpect(jsonPath("$.version").value("test-version"))
                .andExpect(jsonPath("$.author").value("Laurent Sintès"));
    }

    @Test
    void exposesEmbeddedUserAndAdministratorDocumentation() throws Exception {
        mvc.perform(get("/api/v1/documentation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents.length()").value(4))
                .andExpect(jsonPath("$.documents[0].id").value("user-guide"))
                .andExpect(jsonPath("$.documents[0].markdown").isNotEmpty())
                .andExpect(jsonPath("$.documents[1].audience").value("administrator"));
    }

    @Test
    void filtersDocumentationForTheRequestedPage() throws Exception {
        mvc.perform(get("/api/v1/documentation").queryParam("audience", "user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents.length()").value(1))
                .andExpect(jsonPath("$.documents[0].audience").value("user"));
    }
}
