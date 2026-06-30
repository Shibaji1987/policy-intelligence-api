package com.shibajide.policyintelligence.document.api;

import com.shibajide.policyintelligence.document.application.DocumentQueryService;
import com.shibajide.policyintelligence.document.application.DocumentSummary;
import com.shibajide.policyintelligence.security.RetrievalAccessPolicy;
import org.apache.tika.Tika;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@AutoConfigureMockMvc(addFilters = false)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentQueryService queryService;

    @MockitoBean
    private com.shibajide.policyintelligence.document.application.DocumentIngestionService ingestionService;

    @MockitoBean
    private RetrievalAccessPolicy accessPolicy;

    @MockitoBean
    private Tika tika;

    @Test
    void listsDocumentsAsPagedResponse() throws Exception {
        when(queryService.findDocuments(any())).thenReturn(new PageImpl<>(List.of(new DocumentSummary(
                UUID.randomUUID(),
                "Production Data Access Policy",
                "default",
                "Security",
                "Global",
                "Policy",
                "Internal",
                OffsetDateTime.parse("2026-06-30T00:00:00Z"),
                OffsetDateTime.parse("2026-06-30T00:00:00Z")
        ))));

        mockMvc.perform(get("/api/v1/documents?page=0&size=25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Production Data Access Policy"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
