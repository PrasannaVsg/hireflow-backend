package com.hireflow.service;

import com.hireflow.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResumeParserServiceTest {

    private final ResumeParserService service = new ResumeParserService();

    @Test
    void parse_extractsTextFromPlainTextResume() {
        String content = "Jane Doe\nSenior Java Engineer\nSkills: Spring Boot, PostgreSQL, Kafka";
        InputStream in = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        String text = service.parse(in, "text/plain", "jane.txt");

        assertThat(text).contains("Jane Doe").contains("Spring Boot");
    }

    @Test
    void parse_rejectsUnsupportedContentType() {
        InputStream in = new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.parse(in, "image/png", "photo.png"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unsupported resume type");
    }

    @Test
    void parse_throwsWhenNoExtractableText() {
        InputStream in = new ByteArrayInputStream("   \n  \t ".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.parse(in, "text/plain", "empty.txt"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("No extractable text");
    }

    @Test
    void normalize_collapsesWhitespaceAndCapsLength() {
        String messy = "a\t\t\tb   c\n\n\n\nd";
        String normalized = service.normalize(messy);

        assertThat(normalized).isEqualTo("a b c\n\nd");
    }
}
