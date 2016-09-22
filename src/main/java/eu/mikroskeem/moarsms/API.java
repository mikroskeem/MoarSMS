package eu.mikroskeem.moarsms;

public class API {
    private static Platform instance;
    private API(){}
    public static Platform getInstance(){
        return instance;
    }
    public static void setInstance(Platform instance1){
        if(instance != null){
            throw new UnsupportedOperationException("Instance is already set!");
        } else {
            instance = instance1;
        }
    }
}
