package com.vish.fno.model;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

@Slf4j
@Data
@Builder
@AllArgsConstructor
@Document(collection = "minute_option_data")
public class OptionSymbolData {
    @Id
    private OptionMetaData record;
    private List<Candle> data;
}
