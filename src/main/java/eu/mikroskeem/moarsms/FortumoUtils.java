package eu.mikroskeem.moarsms;

import java.util.*;

public class FortumoUtils {
    private static List<String> allowedIPs = new ArrayList<String>(){{
        // TODO: use configuration instead of hardcoding
        add("79.125.125.1");
        add("79.125.5.205");
        add("79.125.5.95");
        add("54.72.6.126");
        add("54.72.6.27");
        add("54.72.6.17");
        add("54.72.6.23");
        add("127.0.0.1"); // will be disabled by allowTest switch
    }};

    /**
     * Check if signature matches
     *
     * @param params Parameters to generate signature from
     * @param secret Secret key
     * @return Whether signature was correct
     */
    public static boolean checkSignature(Map<String, String> params, String secret){
        StringBuilder toHash = new StringBuilder();
        SortedSet<String> keys = new TreeSet<>(params.keySet());
        keys.forEach(key->{
            if(!key.equals("sig"))
                toHash.append(String.format("%s=%s", key, params.get(key)));
        });
        toHash.append(secret);
        return Hash.md5(toHash.toString()).equals(params.get("sig"));
    }

    /**
     * Check if IP address is in whitelist
     * @param ip IP to check
     * @return Whether IP was allowed or not
     */
    public static boolean checkIP(String ip){
        return allowedIPs.stream().filter(i->i.equals(ip)).count() > 0;
    }
}