package com.vish.fno.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@Document(collection = "ticker_history_data")
public class CompressedTicker {
    @Id
    private CandleMetaData id;
    private byte[] compressedTickerData;
}


