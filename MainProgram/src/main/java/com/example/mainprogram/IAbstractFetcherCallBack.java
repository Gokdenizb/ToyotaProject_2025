package com.example.mainprogram;

public interface IAbstractFetcherCallBack {
    void onConnect(String platformName , Boolean status);
    void onDisConnect(String platformName , Boolean status);
    void onRateAvailable(String platformName , String rateName , Rate rate);
    void onRateUpdate(String platformName , String rateName , RateFields rateFields);
    void onRateStatus(String platformName , String rateName , RateStatus rateStatus);
}
