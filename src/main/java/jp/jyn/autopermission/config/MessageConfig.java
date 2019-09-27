package jp.jyn.autopermission.config;

import jp.jyn.jbukkitlib.config.parser.template.StringParser;
import jp.jyn.jbukkitlib.config.parser.template.TemplateParser;
import jp.jyn.jbukkitlib.util.PackagePrivate;
import org.bukkit.configuration.ConfigurationSection;

public class MessageConfig {
    private final static String PREFIX = "[AutoPermission] ";

    public final String formatSeparator;
    public final TemplateParser formatDay;
    public final TemplateParser formatHour;
    public final TemplateParser formatMinute;

    /**
     * player,time
     */
    public final TemplateParser joinBroadcast;
    /**
     * player,time
     */
    public final TemplateParser joinNew;

    /**
     * player,time,group
     */
    public final TemplateParser promote;

    @PackagePrivate
    MessageConfig(ConfigurationSection config) {
        formatSeparator = config.getString("format.separator");
        formatDay = parse(config.getString("format.day"));
        formatHour = parse(config.getString("format.hour"));
        formatMinute = parse(config.getString("format.minute"));

        joinBroadcast = parse(config.getString("join.broadcast"));
        joinNew = parse(config.getString("join.new"));

        promote = parse(config.getString("promote"));
    }

    private static TemplateParser parse(ConfigurationSection config, String key) {
        return parse(PREFIX + config.getString(key));
    }

    private static TemplateParser parse(String value) {
        return StringParser.parse(value);
    }
}
