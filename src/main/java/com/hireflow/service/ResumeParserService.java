package com.hireflow.service;

import com.hireflow.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

@Slf4j
@Service
public class ResumeParserService {

    private static final Set<String> SUPPORTED = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "text/plain");

    private static final int WRITE_LIMIT = 500_000;

    public String parse(InputStream input, String contentType, String filename) {
        if (contentType == null || !SUPPORTED.contains(contentType)) {
            throw new ValidationException("Unsupported resume type: " + contentType);
        }
        try {
            Parser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(WRITE_LIMIT);
            Metadata metadata = new Metadata();
            metadata.set(Metadata.CONTENT_TYPE, contentType);
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);

            parser.parse(input, handler, metadata, new ParseContext());
            String normalized = normalize(handler.toString());

            if (normalized.isBlank()) {
                throw new ValidationException("No extractable text in resume: " + filename);
            }
            log.debug("Parsed resume '{}' -> {} chars", filename, normalized.length());
            return normalized;

        } catch (IOException | SAXException | TikaException e) {
            log.warn("Resume parse failed for '{}': {}", filename, e.getMessage());
            throw new ValidationException("Could not parse resume '" + filename + "': " + e.getMessage());
        }
    }

    String normalize(String raw) {
        String cleaned = raw
                .replaceAll("\\p{Cntrl}&&[^\r\n\t]", " ")
                .replaceAll("[ \t\f ]+", " ")
                .replaceAll("(?m)^[ \t]+", "")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
        return cleaned.length() > 30_000 ? cleaned.substring(0, 30_000) : cleaned;
    }
}
