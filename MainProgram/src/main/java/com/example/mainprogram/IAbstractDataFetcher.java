package com.example.mainprogram;


public interface IAbstractDataFetcher {

    void connect(String platformName , String userId , String userPassword);
    void disConnect(String platformName , String userId , String userPassword);
    void subscribe(String platformName , String rateName);
    void unSubscribe(String platformName , String rateName);

    void setCallBack(IAbstractFetcherCallBack fetcherCallBack);
    void setPlatformName(String platformName);
    String getPlatformName();
}


