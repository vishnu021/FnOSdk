package com.vish.fno.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "minute_option_data")
public record OptionSymbolData(
    @Id OptionMetaData record,
    List<Candle> data
) {
}
