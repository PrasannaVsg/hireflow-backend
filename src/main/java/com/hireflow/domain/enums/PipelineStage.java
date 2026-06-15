package com.hireflow.domain.enums;

public enum PipelineStage {
    SOURCED,
    SCREENING,
    L1_SHORTLIST,
    L1_REJECT,
    L2_SHORTLIST,
    L2_REJECT,
    CLIENT_SHORTLIST,
    CLIENT_REJECTED,
    WAITING_FEEDBACK,
    FINAL_SELECT,
    OFFER_RELEASED,
    HIRED
}
