package com.vish.fno.model.order.activeorder;

import com.vish.fno.model.Task;

import java.util.Date;
import java.util.Map;

public interface ActiveOrder {
    String getIndex();
    Date getDate();
    double getTarget();
    double getStopLoss();
    double getBuyPrice();
    double getSellPrice();
    void setStopLoss(double stopLoss);
    Task getTask();
    void setSellPrice(double sellPrice);
    void setExitTimeStamp(int exitTimeStamp);
    int getBuyQuantity();
    int getLotSize();
    int getSoldQuantity();
    void incrementSoldQuantity(int soldQuantity, double sellOptionPrice);
    String getTag();
    boolean isActive();
    String csvHeader();
    String toCSV();
    String orderLog();
    void setActive(boolean active);
    void closeOrder(double closePrice, int timeIndex, String timestamp);
    Map<String, String> getExtraData();
    void appendExtraData(String key, String value);
    String getTradingSymbol();
    boolean isTargetAchieved(double ltp);
    boolean isStopLossHit(double ltp);
    double getProfit();
}
