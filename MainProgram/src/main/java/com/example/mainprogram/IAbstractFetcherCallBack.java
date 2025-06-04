package com.example.mainprogram;

import com.example.mainprogram.Rate.Rate;
import com.example.mainprogram.Rate.RateFields;
import com.example.mainprogram.Rate.RateStatus;

public interface IAbstractFetcherCallBack {
    void onConnect(String platformName , Boolean status);
    void onDisConnect(String platformName , Boolean status);
    void onRateAvailable(String platformName , String rateName , Rate rate);
    void onRateUpdate(String platformName , String rateName , RateFields rateFields);
    void onRateStatus(String platformName , String rateName , RateStatus rateStatus);
}
