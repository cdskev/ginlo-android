// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.param;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class HttpPostParams {

    private final String url;

    private final Map<String, String> nameValuePairs;

    public HttpPostParams(String url) {
        this.url = url;
        this.nameValuePairs = new LinkedHashMap<>();
    }

    public void addParam(String name,
                         String value) {
        nameValuePairs.put(name, value);
    }

    public Map<String, String> getNameValuePairs() {
        return nameValuePairs;
    }

    public String getUrl() {
        return url;
    }
}
