package com.vish.fno.reader.util;

import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.OrderParams;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OrderUtils {

    /*https://zerodha.com/marketintel/bulletin/305785/stop-loss-market-sl-m-orders-blocked-for-index-options*/

    public static OrderParams createMarketOrderWithParameters(String symbol,
                                                              int orderSize,
                                                              String transactionType,
                                                              String tag) {
        OrderParams orderParams = new OrderParams();
        orderParams.quantity = orderSize;
        orderParams.orderType = Constants.ORDER_TYPE_MARKET;
        orderParams.tradingsymbol = symbol;
        orderParams.product = Constants.PRODUCT_MIS;
        orderParams.exchange = Constants.EXCHANGE_NFO;
        orderParams.validity = Constants.VALIDITY_DAY;
        orderParams.transactionType = transactionType;
        orderParams.triggerPrice = 0.0; // order type is market
        orderParams.tag = tag.length() > 20 ? tag.substring(0,20) : tag; // max tag length is 20
        return orderParams;
    }
}
