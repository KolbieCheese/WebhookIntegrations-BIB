package rudynakodach.github.io.webhookintegrations;

import java.io.InputStream;
import java.util.Properties;

public final class BuildMetadata {
    private static final String DEFAULT_REPOSITORY = "KolbieCheese/WebhookIntegrations-BIB";
    private static final String DEFAULT_REPOSITORY_BRANCH = "master";
    private static final Properties PROPERTIES = load();

    private BuildMetadata() {
    }

    public static int getCurrentBuildNumber() {
        String configuredValue = PROPERTIES.getProperty("plugin.build-number", "67");

        try {
            return Integer.parseInt(configuredValue);
        } catch (NumberFormatException ignored) {
            return 67;
        }
    }

    public static String getPluginRepository() {
        return PROPERTIES.getProperty("plugin.repository", DEFAULT_REPOSITORY);
    }

    public static String getPluginRepositoryBranch() {
        return PROPERTIES.getProperty("plugin.repository-branch", DEFAULT_REPOSITORY_BRANCH);
    }

    private static Properties load() {
        Properties properties = new Properties();

        try (InputStream inputStream = BuildMetadata.class.getClassLoader().getResourceAsStream("build.properties")) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        } catch (Exception ignored) {
        }

        return properties;
    }
}
