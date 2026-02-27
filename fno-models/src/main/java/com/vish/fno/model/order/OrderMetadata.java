package com.vish.fno.model.order;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderMetadata {
    private final int maxHoldDuration;
    private final String subSignal;
}
