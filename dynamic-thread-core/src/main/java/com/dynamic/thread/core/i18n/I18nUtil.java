package com.dynamic.thread.core.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Internationalization utility based on Java ResourceBundle.
 * <p>
 * Supports locale switching at runtime without Spring dependency.
 * Default locale is English. Use {@link #setLocale(Locale)} to change.
 * <p>
 * Usage:
 * <pre>
 *   I18nUtil.get("alarm.rule.thread-usage-high")
 *   I18nUtil.get("alarm.message.threshold-exceeded", poolId, metric, value, unit, threshold, unit)
 * </pre>
 */
public final class I18nUtil {

    private static final String BUNDLE_NAME = "i18n.messages";

    /** Default locale used when no request-scoped locale is set */
    private static volatile Locale defaultLocale = Locale.ENGLISH;

    /** Thread-local locale for request-scoped i18n */
    private static final ThreadLocal<Locale> threadLocale = new ThreadLocal<>();

    private I18nUtil() {
    }

    /**
     * Set the locale for the current thread (request scope).
     *
     * @param locale the locale to use for this thread
     */
    public static void setLocale(Locale locale) {
        if (locale != null) {
            threadLocale.set(locale);
        }
    }

    /**
     * Clear the thread-local locale. Call this after request completion.
     */
    public static void clearLocale() {
        threadLocale.remove();
    }

    /**
     * Set the default locale (used when no thread-local locale is set).
     */
    public static void setDefaultLocale(Locale locale) {
        if (locale != null) {
            defaultLocale = locale;
        }
    }

    /**
     * Get the current effective locale (thread-local > default).
     */
    public static Locale getLocale() {
        Locale tl = threadLocale.get();
        return tl != null ? tl : defaultLocale;
    }

    /**
     * Get a localized message by key.
     *
     * @param key the message key
     * @return the localized message, or the key itself if not found
     */
    public static String get(String key) {
        try {
            return loadBundle(getLocale()).getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    /**
     * Get a localized message by key with parameter substitution.
     * Uses {@link MessageFormat} pattern: {0}, {1}, {2}, etc.
     *
     * @param key  the message key
     * @param args the arguments to substitute
     * @return the formatted localized message
     */
    public static String get(String key, Object... args) {
        String pattern = get(key);
        try {
            return MessageFormat.format(pattern, args);
        } catch (Exception e) {
            return pattern;
        }
    }

    /**
     * Get a localized message for a specific locale (without changing global locale).
     *
     * @param locale the locale to use
     * @param key    the message key
     * @param args   optional arguments
     * @return the formatted localized message
     */
    public static String getForLocale(Locale locale, String key, Object... args) {
        try {
            ResourceBundle rb = loadBundle(locale);
            String pattern = rb.getString(key);
            if (args.length > 0) {
                return MessageFormat.format(pattern, args);
            }
            return pattern;
        } catch (MissingResourceException e) {
            return key;
        }
    }

    private static ResourceBundle loadBundle(Locale locale) {
        try {
            // Use NO_FALLBACK control to prevent JVM default locale from overriding.
            // This ensures Locale.ENGLISH loads messages.properties (root bundle),
            // not messages_zh_CN.properties (JVM default locale on Chinese systems).
            return ResourceBundle.getBundle(BUNDLE_NAME, locale,
                    ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_DEFAULT));
        } catch (MissingResourceException e) {
            // If exact locale not found, try root bundle
            try {
                return ResourceBundle.getBundle(BUNDLE_NAME, Locale.ROOT,
                        ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_DEFAULT));
            } catch (MissingResourceException e2) {
                return ResourceBundle.getBundle(BUNDLE_NAME);
            }
        }
    }
}
