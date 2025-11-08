package com.vish.fno.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "minute_history_data")
public class SymbolData {
    @Id
    private CandleMetaData record;
    private List<Candle> data;
}
