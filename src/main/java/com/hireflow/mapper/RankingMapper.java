package com.hireflow.mapper;

import com.hireflow.domain.Ranking;
import com.hireflow.web.controller.RankingController.RankingResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RankingMapper {

    @Mapping(target = "candidateId", source = "candidate.id")
    @Mapping(target = "candidateName", source = "candidate.fullName")
    RankingResponse toResponse(Ranking ranking);
}
