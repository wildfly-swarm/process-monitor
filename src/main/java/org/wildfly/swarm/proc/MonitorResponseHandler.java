package org.wildfly.swarm.proc;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;

/**
 * @author Heiko Braun
 * @since 28/04/16
 */
public class MonitorResponseHandler implements ResponseHandler<String> {

    private Callback callback;

    public MonitorResponseHandler(Callback callback) {
        this.callback = callback;
    }

    public String handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
        int status = response.getStatusLine().getStatusCode();
        callback.onResponse(status);
        return "";
    }

    @FunctionalInterface
    interface Callback {
        void onResponse(int status);
    }
}
