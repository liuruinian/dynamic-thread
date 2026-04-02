package com.dynamic.thread.core.exception;

/**
 * Exception thrown when configuration parsing fails.
 * This includes YAML, Properties, or JSON configuration parsing errors.
 */
public class ConfigParseException extends DynamicThreadException {

    private static final long serialVersionUID = 1L;

    private final String configType;

    public ConfigParseException(String message) {
        super(message);
        this.configType = null;
    }

    public ConfigParseException(String message, String configType) {
        super(message);
        this.configType = configType;
    }

    public ConfigParseException(String message, Throwable cause) {
        super(message, cause);
        this.configType = null;
    }

    public ConfigParseException(String message, String configType, Throwable cause) {
        super(message, cause);
        this.configType = configType;
    }

    /**
     * Get the type of configuration that failed to parse.
     *
     * @return the config type (e.g., "YAML", "PROPERTIES", "JSON")
     */
    public String getConfigType() {
        return configType;
    }
}
