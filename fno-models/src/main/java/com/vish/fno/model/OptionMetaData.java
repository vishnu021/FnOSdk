package com.vish.fno.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OptionMetaData {
    private String symbol;
    private String date;
    private String expiryDate;
}
