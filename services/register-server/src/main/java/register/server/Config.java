package register.server;

import services.common.ConfigHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum Config {
    baseURI("http://0.0.0.0:5002/"),
    mongoURI("mongodb://mongo:27017"),
    loginURI("http://login-server:5001/"),
    dbName("benutzer"),
    dbAccountCollection("account"),
    corsAllowOrigin("*");

    private static Map<String, List<String>> settings = new HashMap<>();

    private List<String> defaultValue;

    Config(String defaultValue) {
        this.defaultValue = new ArrayList<>();
        this.defaultValue.add(defaultValue);
    }

    Config(String... defaultValues) {
        this.defaultValue = new ArrayList<>(defaultValues.length);
        for (String value :
                defaultValues) {
            this.defaultValue.add(value);
        }

    }

    public static void init(String[] args) throws Exception {
        Config.settings = ConfigHelper.settingsFromCommandLine(args);

        for (Config defaultVal :
                Config.values()) {
            if (!settings.containsKey(defaultVal.name())) {
                settings.put(defaultVal.name(), defaultVal.defaultValue);
            }
        }
    }

    public static String getSettingValue(Config key) {
        return settings.get(key.name()).get(0);
    }

    public static List<String> getSettingValues(Config key) {
        return new ArrayList<>(settings.get(key.name()));
    }
}
