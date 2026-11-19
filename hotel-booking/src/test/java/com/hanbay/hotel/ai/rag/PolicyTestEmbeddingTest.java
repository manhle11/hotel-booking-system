package com.hanbay.hotel.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingOptions;

class PolicyTestEmbeddingTest {
    @Test
    void batchEmbeddingDistinguishesPoliciesSharingCategoryDespiteOverlappingWording() {
        var model = new PolicyPgVectorIntegrationTest.DeterministicTestEmbeddingModel();
        List<Document> documents = new PolicyKnowledgeBaseLoader().load();
        List<float[]> vectors = model.embed(documents, mock(EmbeddingOptions.class), mock(BatchingStrategy.class));

        assertThat(documents)
                .filteredOn(document -> "BOOKING".equals(document.getMetadata().get("category")))
                .extracting(document -> document.getMetadata().get("documentId"))
                .contains("HANBAY-BOOKING-POLICY", "HANBAY-NO-SHOW-POLICY");
        assertThat(vectors).hasSize(documents.size());
        for (int i = 0; i < documents.size(); i++) {
            Document document = documents.get(i);
            String documentId = document.getMetadata().get("documentId").toString();
            float[] vector = vectors.get(i);
            assertThat(vector).containsExactly(model.embed(document));
            assertThat(vector[0]).isEqualTo("HANBAY-CHECK-IN-POLICY".equals(documentId) ? 1f : 0f);
            assertThat(vector[1]).isEqualTo("HANBAY-CANCELLATION-POLICY".equals(documentId) ? 1f : 0f);
            assertThat(vector[2]).isEqualTo("HANBAY-BOOKING-POLICY".equals(documentId) ? 1f : 0f);
            assertThat(vector[10]).isZero();
        }
        assertThat(model.embed("Giờ nhận phòng là mấy giờ?")[0]).isEqualTo(1f);
        assertThat(model.embed("Khách sạn có sân golf không?")[10]).isEqualTo(1f);
    }
}
