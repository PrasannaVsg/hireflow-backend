package com.hireflow.mapper;

import com.hireflow.domain.JobRequisition;
import com.hireflow.web.controller.JobController.JobResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public interface JobMapper {

    @Mapping(target = "locations", expression = "java(splitLocations(job.getLocations()))")
    JobResponse toResponse(JobRequisition job);

    default List<String> splitLocations(String locations) {
        if (locations == null || locations.isBlank()) return Collections.emptyList();
        return Arrays.stream(locations.split(","))
                     .map(String::trim)
                     .filter(s -> !s.isEmpty())
                     .toList();
    }
}
