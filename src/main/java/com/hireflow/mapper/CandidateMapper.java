package com.hireflow.mapper;

import com.hireflow.domain.Candidate;
import com.hireflow.web.controller.CandidateController.CandidateResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CandidateMapper {

    @Mapping(target = "jobId", source = "job.id")
    @Mapping(target = "status", expression = "java(candidate.getStatus().name())")
    CandidateResponse toResponse(Candidate candidate);
}
