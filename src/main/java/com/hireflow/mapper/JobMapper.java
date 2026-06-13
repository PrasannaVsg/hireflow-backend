package com.hireflow.mapper;

import com.hireflow.domain.JobRequisition;
import com.hireflow.web.controller.JobController.JobResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface JobMapper {

    JobResponse toResponse(JobRequisition job);
}
