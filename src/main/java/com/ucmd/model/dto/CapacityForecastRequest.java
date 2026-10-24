package com.ucmd.model.dto;

public class CapacityForecastRequest {

    /** Eğilimin hesaplandığı geçmiş; varsayılan 24 saat, en fazla snapshot saklama süresi. */
    private Integer windowHours;

    public Integer getWindowHours() { return windowHours; }
    public void setWindowHours(Integer windowHours) { this.windowHours = windowHours; }
}
