package com.vish.fno.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "minute_history_data")
public record SymbolData(
    @Id CandleMetaData record,
    List<Candle> data
) {
}
