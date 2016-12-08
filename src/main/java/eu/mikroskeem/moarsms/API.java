package eu.mikroskeem.moarsms;

import lombok.Getter;

public final class API {
    private API(){}
    @Getter private static Platform instance;
    public static void setInstance(Platform instance1){
        if(instance != null){
            throw new UnsupportedOperationException("Instance is already set!");
        } else {
            instance = instance1;
        }
    }
}
