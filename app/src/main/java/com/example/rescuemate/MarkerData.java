package com.example.rescuemate;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MarkerData {
    public double latitude;
    public double longitude;
    public String danger;
    public List<String> confirmedBy = new ArrayList<>();
    public String reportedBy;

    public MarkerData(){
    }

    public MarkerData(double lat, double lon){
        latitude = lat;
        longitude = lon;
    }


}
