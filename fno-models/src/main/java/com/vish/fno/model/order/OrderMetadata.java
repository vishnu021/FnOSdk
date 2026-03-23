package com.vish.fno.model.order;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Builder
public class OrderMetadata {
    private final int maxHoldDuration;
    private final String subSignal;
    @Setter
    private String skipReason;
}
