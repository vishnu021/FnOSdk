package com.vish.fno.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "ticker_history_data")
public record CompressedTicker(
    @Id CandleMetaData id,
    byte[] compressedTickerData
) {
}


