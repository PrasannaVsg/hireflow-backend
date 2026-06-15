package com.hireflow.web.controller;

import com.hireflow.domain.Organisation;
import com.hireflow.exception.ResourceNotFoundException;
import com.hireflow.repository.OrganisationRepository;
import com.hireflow.service.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final OrganisationRepository organisationRepository;

    public record MailSettingsResponse(String mailFrom, String mailReplyTo) {}

    public record MailSettingsRequest(
            @Email String mailFrom,
            @Email String mailReplyTo) {}

    @GetMapping("/mail")
    public MailSettingsResponse getMailSettings() {
        UUID orgId = SecurityUtils.currentOrgId();
        Organisation org = organisationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organisation", orgId));
        return new MailSettingsResponse(org.getMailFrom(), org.getMailReplyTo());
    }

    @PutMapping("/mail")
    public MailSettingsResponse updateMailSettings(@Valid @RequestBody MailSettingsRequest request) {
        UUID orgId = SecurityUtils.currentOrgId();
        Organisation org = organisationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organisation", orgId));
        org.setMailFrom(request.mailFrom());
        org.setMailReplyTo(request.mailReplyTo());
        organisationRepository.save(org);
        return new MailSettingsResponse(org.getMailFrom(), org.getMailReplyTo());
    }
}
