package com.pshdev0.reddy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ListOfStrings {
    private List<String> strings;

    ListOfStrings(List<String> strings) {
        this.strings = strings;
    }

    public List<String> getStrings() { return strings; }
    public void setStrings(List<String> strings) { this.strings = strings; }
}
