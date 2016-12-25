package eu.mikroskeem.moarsms;

import lombok.Getter;
import lombok.Setter;

public final class API {
    private API(){}
    @Getter @Setter private static Platform instance;
}
