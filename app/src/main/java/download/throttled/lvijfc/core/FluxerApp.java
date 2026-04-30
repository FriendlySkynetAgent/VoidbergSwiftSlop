package download.throttled.lvijfc.core;

import android.app.Application;
import android.content.SharedPreferences;

import download.throttled.lvijfc.api.FluxerApi;

public class FluxerApp extends Application {

    private FluxerApi     api;
    private FluxerGateway gateway;

    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferences prefs = getSharedPreferences("fluxer", MODE_PRIVATE);
        String token = prefs.getString("token", null);
        if (token != null) {
            api     = new FluxerApi(token);
            gateway = new FluxerGateway();
            gateway.connect(token, prefs.getString("user_id", ""));
        }
    }

    /** Call after a successful login to wire up the API and gateway. */
    public void initApi(String token) {
        api = new FluxerApi(token);
        if (gateway == null) gateway = new FluxerGateway();
        gateway.connect(token, getMyUserId());
    }

    /** Null after logout. */
    public FluxerApi getApi() { return api; }

    /** Always non-null once initApi() has been called. */
    public FluxerGateway getGateway() { return gateway; }

    /** The logged-in user's snowflake ID, or empty string if not yet stored. */
    public String getMyUserId() {
        return getSharedPreferences("fluxer", MODE_PRIVATE)
                .getString("user_id", "");
    }

    public void logout() {
        if (gateway != null) { gateway.disconnect(); gateway = null; }
        api = null;
        getSharedPreferences("fluxer", MODE_PRIVATE).edit().clear().apply();
    }

    public static FluxerApp get(android.content.Context ctx) {
        return (FluxerApp) ctx.getApplicationContext();
    }
}
