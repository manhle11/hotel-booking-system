package com.hanbay.hotel.ai.rag;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class PolicyKnowledgeBaseLoaderTest {
    @Test
    void indexesEveryPolicyIncludingCancellationAndRescheduling() {
        var documents = new PolicyKnowledgeBaseLoader().load();
        assertThat(documents)
                .filteredOn(
                        d -> "HANBAY-CANCELLATION-POLICY".equals(d.getMetadata().get("documentId")))
                .isNotEmpty()
                .anySatisfy(d -> assertThat(d.getText()).contains("48 giờ"));
        assertThat(documents).anySatisfy(d -> assertThat(d.getText())
                .contains("Đổi ngày trước khi nhận phòng theo gói tiêu chuẩn V2"));
    }

    @Test
    void keepsParentProseAndMixedHeadingLevelsWithoutTreatingCodeAsHeadings() throws Exception {
        String markdown =
                """
                ---
                documentId: TEST
                title: Policy
                category: CANCELLATION
                source: test.md
                version: 1
                hotel: HANBAY HOTEL
                language: vi
                ---
                # Policy
                Đoạn mở đầu.
                ## Điều kiện hủy
                Nội dung cấp hai.
                ### Phí
                Nội dung cấp ba.
                ```text
                ## Nội dung trong mã
                ```
                ## Hoàn tiền
                Nội dung hoàn.
                """;
        var docs = new PolicyKnowledgeBaseLoader()
                .loadResource(new ByteArrayResource(markdown.getBytes(StandardCharsets.UTF_8)));
        assertThat(docs).hasSize(4);
        assertThat(docs.get(0).getText()).contains("Đoạn mở đầu");
        assertThat(docs.get(1).getText()).contains("Nội dung cấp hai");
        assertThat(docs.get(2).getText()).contains("Nội dung cấp ba", "## Nội dung trong mã");
        assertThat(docs.get(3).getText()).contains("Nội dung hoàn");
    }
}
